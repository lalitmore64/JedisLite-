package com.jedislite.resp;

import java.nio.charset.StandardCharsets;

public final class RespFrameDecoder {

    private RespFrameDecoder() {
    }

    public static int findCompleteFrame(byte[] buffer, int offset, int length) {
        if (length < 1) {
            return -1;
        }

        byte prefix = buffer[offset];
        return switch (prefix) {
            case RespValue.SIMPLE_STRING_PREFIX, RespValue.ERROR_PREFIX, RespValue.INTEGER_PREFIX ->
                    findLineFrame(buffer, offset, length);
            case RespValue.BULK_STRING_PREFIX ->
                    findBulkStringFrame(buffer, offset, length);
            case RespValue.ARRAY_PREFIX ->
                    findArrayFrame(buffer, offset, length);
            default ->
                    throw new RespProtocolException("Unexpected RESP prefix byte: '" + (char) prefix + "' (0x" + Integer.toHexString(prefix & 0xFF) + ")");
        };
    }

    private static int findLineFrame(byte[] buffer, int offset, int length) {
        int crlfIndex = findCrlf(buffer, offset, length);
        if (crlfIndex == -1) {
            return -1;
        }
        return (crlfIndex + 2) - offset;
    }

    private static int findBulkStringFrame(byte[] buffer, int offset, int length) {
        int crlfIndex = findCrlf(buffer, offset, length);
        if (crlfIndex == -1) {
            return -1;
        }

        int lengthLineBytes = (crlfIndex + 2) - offset;
        String lengthStr = new String(buffer, offset + 1, crlfIndex - (offset + 1), StandardCharsets.US_ASCII);
        long payloadLength;
        try {
            payloadLength = Long.parseLong(lengthStr);
        } catch (NumberFormatException e) {
            throw new RespProtocolException("Malformed bulk string length: '" + lengthStr + "'", e);
        }

        if (payloadLength == -1) {
            return lengthLineBytes;
        }
        if (payloadLength < -1) {
            throw new RespProtocolException("Invalid bulk string length: " + payloadLength);
        }

        long totalFrameBytes = lengthLineBytes + payloadLength + 2;
        if (totalFrameBytes > Integer.MAX_VALUE) {
            throw new RespProtocolException("Bulk string length exceeds integer limit");
        }

        if (length < totalFrameBytes) {
            return -1;
        }

        int intTotal = (int) totalFrameBytes;

        int trailingCr = offset + intTotal - 2;
        int trailingLf = offset + intTotal - 1;
        if (buffer[trailingCr] != RespValue.CR || buffer[trailingLf] != RespValue.LF) {
            throw new RespProtocolException("Bulk string data not terminated by CRLF");
        }

        return intTotal;
    }

    private static int findArrayFrame(byte[] buffer, int offset, int length) {
        int crlfIndex = findCrlf(buffer, offset, length);
        if (crlfIndex == -1) {
            return -1;
        }

        int countLineBytes = (crlfIndex + 2) - offset;
        String countStr = new String(buffer, offset + 1, crlfIndex - (offset + 1), StandardCharsets.US_ASCII);
        long count;
        try {
            count = Long.parseLong(countStr);
        } catch (NumberFormatException e) {
            throw new RespProtocolException("Malformed array count: '" + countStr + "'", e);
        }

        if (count == -1 || count == 0) {
            return countLineBytes;
        }
        if (count < -1) {
            throw new RespProtocolException("Invalid array length: " + count);
        }

        int currentOffset = crlfIndex + 2;
        int remainingLength = length - countLineBytes;

        for (int i = 0; i < count; i++) {
            int elementFrameBytes = findCompleteFrame(buffer, currentOffset, remainingLength);
            if (elementFrameBytes == -1) {
                return -1;
            }
            currentOffset += elementFrameBytes;
            remainingLength -= elementFrameBytes;
        }

        return currentOffset - offset;
    }

    private static int findCrlf(byte[] buffer, int offset, int length) {
        int limit = offset + length - 1;
        for (int i = offset; i < limit; i++) {
            if (buffer[i] == RespValue.CR && buffer[i + 1] == RespValue.LF) {
                return i;
            }
        }
        return -1;
    }
}

