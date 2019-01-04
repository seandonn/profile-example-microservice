package com.example.profile.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.uuid.EthernetAddress;
import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedGenerator;
import com.flipkart.zjsonpatch.JsonDiff;
import com.flipkart.zjsonpatch.JsonPatch;
import com.flipkart.zjsonpatch.JsonPatchApplicationException;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import com.jayway.jsonpath.spi.json.JsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import com.jayway.jsonpath.spi.mapper.MappingProvider;
import org.apache.deltaspike.core.api.config.ConfigProperty;
import org.rocksdb.*;
import org.slf4j.Logger;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.*;
import java.util.*;

@ApplicationScoped
public class ProfileService {

  protected RocksDB db;

  // constant indices for column families list
  protected static final int DEFAULT = 0;
  protected static final int SALTS = 1;
  protected static final int HISTORY = 1;
  protected List<ColumnFamilyHandle> columnFamilies;

  protected TimeBasedGenerator generator;
  protected ObjectMapper mapper;

  @Inject
  protected EncryptionService encryption;

  @Inject
  protected Logger log;

  @Inject @ConfigProperty(name="rocksdb.path")
  protected String path;

  @Inject @ConfigProperty(name="node.id")
  protected String nodeId;

  protected WriteOptions writeOpts;

  protected WriteLockCache writeLockCache;

  @PostConstruct
  protected void init() throws RocksDBException {
    RocksDB.loadLibrary();
    try (final ColumnFamilyOptions cfOpts = new ColumnFamilyOptions().optimizeUniversalStyleCompaction()) {
      // list of column family descriptors, first entry must always be default column family
      final List<ColumnFamilyDescriptor> cfDescriptors = Arrays.asList(
          new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, cfOpts),
          new ColumnFamilyDescriptor("salts".getBytes(), cfOpts),
          new ColumnFamilyDescriptor("history".getBytes(), cfOpts)
      );

      // a list which will hold the handles for the column families once the db is opened
      columnFamilies = new ArrayList<>();

      try (final DBOptions options = new DBOptions()
          .setCreateIfMissing(true)
          .setCreateMissingColumnFamilies(true)) {
        db = RocksDB.open(options, path, cfDescriptors, columnFamilies);
      }
    }
    writeOpts = new WriteOptions();
    // set writes to be synchronous
    writeOpts.setSync(true);

    // set the node for the type 1 UUIDs we'll generate
    EthernetAddress node = new EthernetAddress(nodeId);
    generator = Generators.timeBasedGenerator(node);
    // set object mapper
    mapper = new ObjectMapper();
    // set write locks
    writeLockCache = new WriteLockCache();
    // set up JsonPath
    Configuration.setDefaults(new Configuration.Defaults() {
      private final JsonProvider jsonProvider = new JacksonJsonNodeJsonProvider();
      private final MappingProvider mappingProvider = new JacksonMappingProvider();
      @Override
      public JsonProvider jsonProvider() {
        return jsonProvider;
      }

      @Override
      public Set<Option> options() {
        return EnumSet.of(Option.DEFAULT_PATH_LEAF_TO_NULL, Option.SUPPRESS_EXCEPTIONS);
      }

      @Override
      public MappingProvider mappingProvider() {
        return mappingProvider;
      }
    });
  }

  @PreDestroy
  protected void cleanup() {
    writeOpts.close();
    db.close();
  }

  protected boolean contains(byte[] key) {
    try {
      int bytes = db.get(key, new byte[0]);
      return bytes > 0;
    } catch (RocksDBException e) {
      log.error("contains {}", e, new String(key));
      return false;
    }
  }

  public boolean contains(String uuid) {
    return contains(uuid.getBytes());
  }

  // internal read method that is reused by write and update operations
  // this version requires an allocated byte array to be passed in where the salt will be stored
  protected JsonNode internalRead(String who, byte[] key, byte[] salt) throws RocksDBException, IOException {
    byte[] val = db.get(key);
    if (val == null)
      return null;
    int bytesRead = db.get(columnFamilies.get(SALTS),key,salt);
    if (bytesRead == 0)
      return null;
    try (ByteArrayInputStream bin = new ByteArrayInputStream(val)) {
      byte[] iv = new byte[EncryptionService.IV_SIZE];
      bin.read(iv);
      try (InputStream in = encryption.getDecryptedIn(bin, salt, iv)) {
        return mapper.readTree(in);
      }
    }
  }

  public JsonNode read(String who, String uuid) {
    byte[] key = uuid.getBytes();
    try {
      byte[] salt = new byte[EncryptionService.SALT_SIZE];
      return internalRead(who, key, salt);
    } catch (RocksDBException | IOException e) {
      log.error("{} reading {}",e,who,uuid);
      return null;
    }
  }

  public JsonNode readPath(String who, String uuid, String path) {
    byte[] key = uuid.getBytes();
    byte[] salt = new byte[EncryptionService.SALT_SIZE];
    try {
      JsonNode obj = internalRead(who, key, salt);
      return JsonPath.read(obj, path);
    } catch (RocksDBException | IOException e) {
      log.error("{} reading {}",e,who,uuid);
      return null;
    }
  }

  // internal upsert method that powers insert, write, and update
  // should only be called after write lock has been acquired
  protected boolean internalUpsert(String who, String uuid, JsonNode patch, JsonNode body, byte[] salt) throws IOException {
    byte[] key = uuid.getBytes();
    ObjectNode obj = mapper.createObjectNode();
    obj.put("author",who);
    obj.set("patch",patch);
    byte[] historyKey = historyKey(uuid+":"+System.currentTimeMillis());
    boolean insertFlag = (body == null);  // if there is no original body then this is an insert
    try {
      if (insertFlag) {
        body = mapper.createObjectNode();
        salt = encryption.generateSalt();
      }
      JsonPatch.applyInPlace(patch, body);
      byte[] iv = encryption.generateIV();
      try (ByteArrayOutputStream patchOut = new ByteArrayOutputStream()) {
        patchOut.write(iv);
        try (OutputStream out = encryption.getEncryptedOut(patchOut, salt, iv)) {
          mapper.writeValue(out, obj);
        }
        byte[] patchBytes = patchOut.toByteArray();
        try (ByteArrayOutputStream bodyOut = new ByteArrayOutputStream()) {
          byte[] iv2 = encryption.generateIV();
          bodyOut.write(iv2);
          try (OutputStream out = encryption.getEncryptedOut(bodyOut, salt, iv2)) {
            mapper.writeValue(out, body);
          }
          byte[] bodyBytes = bodyOut.toByteArray();
          try (final WriteBatch batch = new WriteBatch()) {
            batch.put(key, bodyBytes);
            batch.put(columnFamilies.get(HISTORY), historyKey, patchBytes);
            if (insertFlag)
              batch.put(columnFamilies.get(SALTS), key, salt);
            db.write(writeOpts, batch);
          }
        }
      }
    } catch (RocksDBException e) {
      throw new IOException("RocksDB error upserting uuid "+uuid,e);
    } catch (JsonPatchApplicationException pe) {
      // patch failed, likely due to test op
      return false;
    }
    return true;
  }

  public String insert(String who, JsonNode body) throws IOException {
    // create a patch from a blank object to use internal update
    ObjectNode blank = mapper.createObjectNode();
    JsonNode patch = JsonDiff.asJson(blank, body);
    String uuid;
    byte[] key;
    do {
      uuid = generator.generate().toString();
      key = uuid.getBytes();
    } while (contains(key) || writeLockCache.contains(uuid));
    synchronized (writeLockCache.acquire(uuid)) {
      internalUpsert(who, uuid, patch, null, null);
      return uuid;
    }
  }

  public boolean write(String who, String uuid, JsonNode body) throws IOException {
    byte[] key = uuid.getBytes();
    byte[] salt = new byte[EncryptionService.SALT_SIZE];
    try {
      synchronized (writeLockCache.acquire(uuid)) {
        JsonNode original = internalRead(who, key, salt);
        if (salt == null)
          throw new IOException("uuid " + uuid + " does not exist");
        JsonNode patch = JsonDiff.asJson(original, body);
        return internalUpsert(who, uuid, patch, original, salt);
      }
    } catch (RocksDBException  e) {
      throw new IOException("RocksDB error updating uuid "+uuid,e);
    }
  }

  public boolean update(String who, String uuid, JsonNode patch) throws IOException {
    byte[] key = uuid.getBytes();
    byte[] salt = new byte[EncryptionService.SALT_SIZE];
    try {
      synchronized (writeLockCache.acquire(uuid)) {
        JsonNode original = internalRead(who, key, salt);
        if (salt == null)
          throw new IOException("uuid " + uuid + " does not exist");
        return internalUpsert(who, uuid, patch, original, salt);
      }
    } catch (RocksDBException  e) {
      throw new IOException("RocksDB error updating uuid "+uuid,e);
    }
  }

  public boolean delete(String who, String uuid) {
    try {
      byte[] key = uuid.getBytes();
      if (!contains(key))
        return false;
      try (final WriteBatch batch = new WriteBatch()) {
        batch.delete(uuid.getBytes());
        // don't delete salt as it is needed to access history
//        batch.delete(columnFamilies.get(SALTS),uuid.getBytes());
        db.write(writeOpts, batch);
      }
      return true;
    }
    catch (RocksDBException e) {
      log.error("{} deleting {}",e,who,uuid);
      return false;
    }
  }

  public long count() {
    long count = 0;
    try (RocksIterator it = db.newIterator()) {
      for (it.seekToFirst(); it.isValid(); it.next())
        count++;
    }
    return count;
  }

  protected byte[] historyKey(String prefix) {
    String lastKey = null;
    try (RocksIterator it = db.newIterator(columnFamilies.get(HISTORY))) {
      for (it.seek(prefix.getBytes()); it.isValid(); it.next()) {
        String key = new String(it.key());
        if (key.startsWith(prefix))
          lastKey = key;
        else
          break;
      }
      if (lastKey == null)
        return (prefix+":0").getBytes();
      // found a previous key with this prefix, so increment the counter portion
      String[] parts = lastKey.split(":");
      int count = Integer.parseInt(parts[2]);
      return (prefix+":"+(count+1)).getBytes();
    }
  }
}
