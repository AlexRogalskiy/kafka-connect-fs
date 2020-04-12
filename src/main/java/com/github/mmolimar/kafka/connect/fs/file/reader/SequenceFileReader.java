package com.github.mmolimar.kafka.connect.fs.file.reader;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.*;
import org.apache.hadoop.util.ReflectionUtils;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.errors.ConnectException;

import java.io.EOFException;
import java.io.IOException;
import java.util.Map;
import java.util.NoSuchElementException;

import static com.github.mmolimar.kafka.connect.fs.FsSourceTaskConfig.FILE_READER_PREFIX;

public class SequenceFileReader extends AbstractFileReader<SequenceFileReader.SequenceRecord<Writable, Writable>> {

    public static final String FIELD_NAME_KEY_DEFAULT = "key";
    public static final String FIELD_NAME_VALUE_DEFAULT = "value";

    private static final int DEFAULT_BUFFER_SIZE = 4096;
    private static final String FILE_READER_SEQUENCE = FILE_READER_PREFIX + "sequence.";
    private static final String FILE_READER_SEQUENCE_FIELD_NAME_PREFIX = FILE_READER_SEQUENCE + "field_name.";

    public static final String FILE_READER_BUFFER_SIZE = FILE_READER_SEQUENCE + "buffer_size";
    public static final String FILE_READER_SEQUENCE_FIELD_NAME_KEY = FILE_READER_SEQUENCE_FIELD_NAME_PREFIX + "key";
    public static final String FILE_READER_SEQUENCE_FIELD_NAME_VALUE = FILE_READER_SEQUENCE_FIELD_NAME_PREFIX + "value";

    private final SequenceFile.Reader reader;
    private final Writable key, value;
    private final Schema schema;
    private String keyFieldName, valueFieldName;
    private long recordIndex, hasNextIndex;
    private boolean hasNext;
    private boolean closed;

    public SequenceFileReader(FileSystem fs, Path filePath, Map<String, Object> config) throws IOException {
        super(fs, filePath, new SeqToStruct(), config);

        this.reader = new SequenceFile.Reader(fs.getConf(),
                SequenceFile.Reader.file(filePath),
                SequenceFile.Reader.bufferSize(fs.getConf().getInt(FILE_READER_BUFFER_SIZE, DEFAULT_BUFFER_SIZE)));
        this.key = (Writable) ReflectionUtils.newInstance(reader.getKeyClass(), fs.getConf());
        this.value = (Writable) ReflectionUtils.newInstance(reader.getValueClass(), fs.getConf());
        this.schema = SchemaBuilder.struct()
                .field(keyFieldName, getSchema(this.key))
                .field(valueFieldName, getSchema(this.value))
                .build();
        this.recordIndex = this.hasNextIndex = -1;
        this.hasNext = false;
        this.closed = false;
    }

    @Override
    protected void configure(Map<String, String> config) {
        this.keyFieldName = config.getOrDefault(FILE_READER_SEQUENCE_FIELD_NAME_KEY, FIELD_NAME_KEY_DEFAULT);
        this.valueFieldName = config.getOrDefault(FILE_READER_SEQUENCE_FIELD_NAME_VALUE, FIELD_NAME_VALUE_DEFAULT);
    }

    Schema getSchema(Writable writable) {
        if (writable instanceof ByteWritable) {
            return SchemaBuilder.INT8_SCHEMA;
        } else if (writable instanceof ShortWritable) {
            return SchemaBuilder.INT16_SCHEMA;
        } else if (writable instanceof IntWritable) {
            return SchemaBuilder.INT32_SCHEMA;
        } else if (writable instanceof LongWritable) {
            return SchemaBuilder.INT64_SCHEMA;
        } else if (writable instanceof FloatWritable) {
            return SchemaBuilder.FLOAT32_SCHEMA;
        } else if (writable instanceof DoubleWritable) {
            return SchemaBuilder.FLOAT64_SCHEMA;
        } else if (writable instanceof BytesWritable) {
            return SchemaBuilder.BYTES_SCHEMA;
        } else if (writable instanceof BooleanWritable) {
            return SchemaBuilder.BOOLEAN_SCHEMA;
        }
        return SchemaBuilder.STRING_SCHEMA;
    }

    @Override
    public boolean hasNext() {
        if (closed) throw new IllegalStateException("Reader already closed.");
        try {
            if (hasNextIndex == -1 || hasNextIndex == recordIndex) {
                hasNextIndex++;
                incrementOffset();
                hasNext = reader.next(key, value);
            }
            return hasNext;
        } catch (EOFException eofe) {
            return false;
        } catch (IOException ioe) {
            throw new ConnectException(ioe);
        }
    }

    @Override
    protected SequenceRecord<Writable, Writable> nextRecord() {
        if (!hasNext()) {
            throw new NoSuchElementException("There are no more records in file: " + getFilePath());
        }
        recordIndex++;
        return new SequenceRecord<>(schema, keyFieldName, key, valueFieldName, value);
    }

    @Override
    public void seek(long offset) {
        if (offset < 0) {
            throw new IllegalArgumentException("Record offset must be greater than 0");
        }
        try {
            reader.sync(offset);
            hasNextIndex = recordIndex = offset;
            hasNext = false;
            setOffset(offset - 1);
        } catch (IOException ioe) {
            throw new ConnectException("Error seeking file " + getFilePath(), ioe);
        }
    }

    @Override
    public void close() throws IOException {
        closed = true;
        reader.close();
    }

    static class SeqToStruct implements ReaderAdapter<SequenceRecord<Writable, Writable>> {

        @Override
        public Struct apply(SequenceRecord<Writable, Writable> record) {
            return new Struct(record.schema)
                    .put(record.keyFieldName, toSchemaValue(record.key))
                    .put(record.valueFieldName, toSchemaValue(record.value));
        }

        Object toSchemaValue(Writable writable) {
            if (writable instanceof ByteWritable) {
                return ((ByteWritable) writable).get();
            } else if (writable instanceof ShortWritable) {
                return ((ShortWritable) writable).get();
            } else if (writable instanceof IntWritable) {
                return ((IntWritable) writable).get();
            } else if (writable instanceof LongWritable) {
                return ((LongWritable) writable).get();
            } else if (writable instanceof FloatWritable) {
                return ((FloatWritable) writable).get();
            } else if (writable instanceof DoubleWritable) {
                return ((DoubleWritable) writable).get();
            } else if (writable instanceof BytesWritable) {
                return ((BytesWritable) writable).getBytes();
            } else if (writable instanceof BooleanWritable) {
                return ((BooleanWritable) writable).get();
            }
            return writable.toString();
        }
    }

    static class SequenceRecord<T, U> {

        private final Schema schema;
        private final String keyFieldName;
        private final T key;
        private final String valueFieldName;
        private final U value;

        SequenceRecord(Schema schema, String keyFieldName, T key, String valueFieldName, U value) {
            this.schema = schema;
            this.keyFieldName = keyFieldName;
            this.key = key;
            this.valueFieldName = valueFieldName;
            this.value = value;
        }

    }
}
