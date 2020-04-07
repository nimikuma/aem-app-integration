package com.alexanderberndt.appintegration.engine.validators;

import com.alexanderberndt.appintegration.engine.pipeline.api.PipelineFilter;
import com.alexanderberndt.appintegration.engine.pipeline.api.ProcessingContext;

import javax.annotation.Nonnull;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class FileSizeValidator implements PipelineFilter<InputStream, InputStream> {


    @Override
    public String getName() {
        return "file-size-validator";
    }

    @Override
    public Class<InputStream> getInputType() {
        return InputStream.class;
    }

    @Override
    public Class<InputStream> getOutputType() {
        return InputStream.class;
    }

    @Override
    public InputStream filter(ProcessingContext context, InputStream input) {
        return new ByteCountingInputStream(input);
    }

    private class ByteCountingInputStream extends FilterInputStream {

        private long byteCount;

        private long markedByteCount;

        public ByteCountingInputStream(InputStream input) {
            super(input);
        }

        private void incrementByteCount(long delta) {
            byteCount += delta;
        }


        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value != -1) {
                incrementByteCount(1);
            }
            return value;
        }

        @Override
        public int read(@Nonnull byte[] b) throws IOException {
            int size = super.read(b);
            if (size != -1) {
                incrementByteCount(size);
            }
            return size;
        }

        @Override
        public int read(@Nonnull byte[] b, int off, int len) throws IOException {
            int size = super.read(b, off, len);
            if (size != -1) {
                incrementByteCount(size);
            }
            return size;
        }

        @Override
        public long skip(long n) throws IOException {
            long size = super.skip(n);
            incrementByteCount(size);
            return size;
        }

        @Override
        public synchronized void mark(int readlimit) {
            super.mark(readlimit);
            markedByteCount = byteCount;
        }

        @Override
        public synchronized void reset() throws IOException {
            super.reset();
            byteCount = markedByteCount;
        }

        @Override
        public void close() throws IOException {
            super.close();
            if (byteCount < 1000) {
                System.out.println("byte-count of " + byteCount + " is less than threshold of " + 1000);
            }
        }
    }
}
