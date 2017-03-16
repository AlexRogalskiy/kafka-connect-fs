package com.github.mmolimar.kafka.connect.fs.file.reader;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.kafka.connect.data.Struct;

import java.util.Map;
import java.util.stream.Collectors;

public abstract class AbstractFileReader<T> implements FileReader {

    public static final String FILE_READER_PREFIX_CONF = "file.reader";

    private final Path filePath;
    private ReaderAdapter<T> adapter;

    public AbstractFileReader(FileSystem fs, Path filePath, ReaderAdapter adapter, Map<String, Object> config) {
        if (fs == null || filePath == null) {
            throw new IllegalArgumentException("fileSystem and filePath are required");
        }
        this.filePath = filePath;
        this.adapter = adapter;

        Map<String, Object> readerConf = config.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(FILE_READER_PREFIX_CONF))
                .collect(Collectors.toMap(entry -> entry.getKey(), entry -> entry.getValue()));
        configure(readerConf);
    }

    protected abstract void configure(Map<String, Object> config);

    @Override
    public Path getFilePath() {
        return filePath;
    }

    public final Struct next() {
        return adapter.apply(nextRecord());
    }

    protected abstract T nextRecord();

}
