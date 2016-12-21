package org.z3950.zing.cql.cql2pgjson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import junitparams.mappers.DataMapper;

/**
 * Maps lines with tab separated values to lines with pipe separated values.
 */
public class TsvWithHeaderMapper implements DataMapper {
    private String [] searchList  = { "|",   "\t" };
    private String [] replaceList = { "\\|", "|"  };

    @Override
    public Object[] map(Reader reader) {
        BufferedReader bufferedReader = new BufferedReader(reader);
        List<String> result = new LinkedList<String>();
        boolean firstLine = true;
        try {
            while (true) {
                String line = bufferedReader.readLine();
                if (line == null) {
                    break;
                }
                if (firstLine) {
                    // skip first line
                    firstLine = false;
                    continue;
                }
                // replace each | by \| and replace each tab by |
                line = StringUtils.replaceEach(line, searchList, replaceList);
                result.add(line);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return result.toArray();
    }

}
