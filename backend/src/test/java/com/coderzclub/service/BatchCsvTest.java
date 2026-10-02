package com.coderzclub.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchCsvTest {
    @Test
    void escapesQuotesCommasAndFormulas() {
        assertEquals("\"a,b\"", BatchCsv.escape("a,b"));
        assertEquals("\"a\"\"b\"", BatchCsv.escape("a\"b"));
        assertEquals("'=1+1", BatchCsv.escape("=1+1"));
        assertEquals("'+cmd", BatchCsv.escape("+cmd"));
        assertEquals("'-1", BatchCsv.escape("-1"));
        assertEquals("'@x", BatchCsv.escape("@x"));
        assertEquals("' =1+1", BatchCsv.escape(" =1+1"));
        assertEquals("'+SUM(A1:A2)", BatchCsv.escape("+SUM(A1:A2)"));
        assertEquals("\"line\nbreak\"", BatchCsv.escape("line\nbreak"));
        assertEquals("'-1+2", BatchCsv.escape("-1+2"));
    }

    @Test
    void percentageUsesCellDenominator() {
        assertEquals("33.33", BatchCsv.percentage(1, 3));
        assertEquals("0.00", BatchCsv.percentage(0, 0));
        assertTrue(BatchCsv.needsFormulaGuard("=hijack"));
    }
}
