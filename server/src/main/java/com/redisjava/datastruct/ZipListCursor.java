package com.redisjava.datastruct;

/**
 * Cursor for iterating over ZipList entries.
 */
public class ZipListCursor {

    private final ZipList list;
    private int offset;

    public ZipListCursor(ZipList list) {
        this.list = list;
        if (list.getLen() == 0) {
            this.offset = -1;
        } else {
            this.offset = list.getHeaderSize();
        }
    }

    public boolean next() {
        if (offset < 0) {
            return false;
        }
        int len = list.getEntryLength(offset);
        int nextOffset = offset + 4 + len;
        if (nextOffset >= list.getUsedBytes()) {
            offset = -1;
            return false;
        }
        offset = nextOffset;
        return true;
    }

    public RString read() {
        if (offset < 0) {
            return null;
        }
        int len = list.getEntryLength(offset);
        long dataAddress = list.getEntryDataAddress(offset);
        return new RString(dataAddress, len);
    }
}
