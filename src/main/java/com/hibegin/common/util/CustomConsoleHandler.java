package com.hibegin.common.util;

import java.io.OutputStream;
import java.util.logging.ConsoleHandler;

public class CustomConsoleHandler extends ConsoleHandler {

    @Override
    protected void setOutputStream(OutputStream out) throws SecurityException {
        super.setOutputStream(out);
    }
}
