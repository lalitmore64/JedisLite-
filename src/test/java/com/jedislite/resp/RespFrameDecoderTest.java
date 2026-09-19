package com.jedislite.resp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RespFrameDecoderTest {

    @Test
    @DisplayName("Detects complete Simple String, Error, and Integer frames")
    void detectsSimpleFrames() {
        byte[] ss = "+OK\r\n".getBytes(StandardCharsets.UTF_8);
        assertThat(RespFrameDecoder.findCompleteFrame(ss, 0, ss.length)).isEqualTo(5);

        byte[] err = "-ERR test\r\n".getBytes(StandardCharsets.UTF_8);
        assertThat(RespFrameDecoder.findCompleteFrame(err, 0, err.length)).isEqualTo(11);

        byte[] num = ":12345\r\n".getBytes(StandardCharsets.UTF_8);
        assertThat(RespFrameDecoder.findCompleteFrame(num, 0, num.length)).isEqualTo(8);
    }

    @Test
    @DisplayName("Returns -1 for partial Simple String or line")
    void returnsMinusOneForPartialLine() {
        byte[] partial = "+OK\r".getBytes(StandardCharsets.UTF_8);
        assertThat(RespFrameDecoder.findCompleteFrame(partial, 0, partial.length)).isEqualTo(-1);

        byte[] partial2 = "+OK".getBytes(StandardCharsets.UTF_8);
        assertThat(RespFrameDecoder.findCompleteFrame(partial2, 0, partial2.length)).isEqualTo(-1);
    }

    @Test
    @DisplayName("Detects complete Bulk Strings including null and empty")
    void detectsBulkStringFrames() {
        byte[] standard = "$5\r\nhello\r\n".getBytes(StandardCharsets.UTF_8);
        assertThat(RespFrameDecoder.findCompleteFrame(standard, 0, standard.length)).isEqualTo(standard.length);

        byte[] empty = "$0\r\n\r\n".getBytes(StandardCharsets.UTF_8);
        assertThat(RespFrameDecoder.findCompleteFrame(empty, 0, empty.length)).isEqualTo(empty.length);

        byte[] nullBs = "$-1\r\n".getBytes(StandardCharsets.UTF_8);
        assertThat(RespFrameDecoder.findCompleteFrame(nullBs, 0, nullBs.length)).isEqualTo(nullBs.length);
    }

    @Test
    @DisplayName("Returns -1 for partial Bulk String payload")
    void returnsMinusOneForPartialBulkString() {

        byte[] partialPayload = "$5\r\nhel".getBytes(StandardCharsets.UTF_8);
        assertThat(RespFrameDecoder.findCompleteFrame(partialPayload, 0, partialPayload.length)).isEqualTo(-1);

        byte[] partialCrlf = "$5\r\nhello\r".getBytes(StandardCharsets.UTF_8);
        assertThat(RespFrameDecoder.findCompleteFrame(partialCrlf, 0, partialCrlf.length)).isEqualTo(-1);
    }

    @Test
    @DisplayName("Detects complete Arrays and handles pipelined multi-frames")
    void detectsArrayAndPipelinedFrames() {
        byte[] cmd = "*2\r\n$4\r\nECHO\r\n$5\r\nhello\r\n".getBytes(StandardCharsets.UTF_8);
        assertThat(RespFrameDecoder.findCompleteFrame(cmd, 0, cmd.length)).isEqualTo(cmd.length);

        byte[] cmd2 = "*1\r\n$4\r\nPING\r\n".getBytes(StandardCharsets.UTF_8);
        byte[] combined = new byte[cmd.length + cmd2.length];
        System.arraycopy(cmd, 0, combined, 0, cmd.length);
        System.arraycopy(cmd2, 0, combined, cmd.length, cmd2.length);

        int firstFrameLen = RespFrameDecoder.findCompleteFrame(combined, 0, combined.length);
        assertThat(firstFrameLen).isEqualTo(cmd.length);

        int secondFrameLen = RespFrameDecoder.findCompleteFrame(combined, firstFrameLen, combined.length - firstFrameLen);
        assertThat(secondFrameLen).isEqualTo(cmd2.length);
    }

    @Test
    @DisplayName("Returns -1 for partial Array elements")
    void returnsMinusOneForPartialArray() {

        byte[] partialArray = "*2\r\n$4\r\nECHO\r\n".getBytes(StandardCharsets.UTF_8);
        assertThat(RespFrameDecoder.findCompleteFrame(partialArray, 0, partialArray.length)).isEqualTo(-1);
    }

    @Test
    @DisplayName("Throws on invalid prefix or bad CRLF termination")
    void throwsOnMalformedFrames() {
        byte[] badPrefix = "!BAD\r\n".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> RespFrameDecoder.findCompleteFrame(badPrefix, 0, badPrefix.length))
                .isInstanceOf(RespProtocolException.class);

        byte[] badCrlf = "$5\r\nhelloXX".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> RespFrameDecoder.findCompleteFrame(badCrlf, 0, badCrlf.length))
                .isInstanceOf(RespProtocolException.class);
    }
}

