package com.coderzclub.service;

import com.coderzclub.model.SubmissionJob;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Versioned line-v1 harness. The submitted code is the body of solve(input_text)
 * and each testcase input must be one line. This service is opt-in per problem.
 */
@Service
public class FunctionHarnessService {
    public static final String FORMAT_VERSION = "line-v1";

    public boolean supports(Integer languageId, String code, List<SubmissionJob.TestCase> publicTests,
                           List<SubmissionJob.TestCase> hiddenTests, String testcaseVersion) {
        if (!FORMAT_VERSION.equals(testcaseVersion)) return false;
        if (code == null || code.isBlank()) return false;
        if (!(languageId == 62 || languageId == 54 || languageId == 71)) return false;
        if (looksLikeFullProgram(code)) return false;
        List<SubmissionJob.TestCase> all = new ArrayList<>();
        if (publicTests != null) all.addAll(publicTests);
        if (hiddenTests != null) all.addAll(hiddenTests);
        return !all.isEmpty() && all.stream().allMatch(test -> test != null
            && test.getInput() != null && !test.getInput().contains("\n") && !test.getInput().contains("\r"));
    }

    private boolean looksLikeFullProgram(String code) {
        String normalized = code.toLowerCase();
        return normalized.contains("public class") || normalized.contains("static void main")
            || normalized.contains("int main(") || normalized.contains("#include")
            || normalized.contains("def solve(") || normalized.contains("if __name__");
    }

    public String wrap(Integer languageId, String body) {
        return switch (languageId) {
            case 62 -> javaWrapper(body);
            case 54 -> cppWrapper(body);
            case 71 -> pythonWrapper(body);
            default -> throw new IllegalArgumentException("Unsupported harness language");
        };
    }

    public String buildInput(List<SubmissionJob.TestCase> testCases) {
        StringBuilder input = new StringBuilder().append(testCases.size()).append('\n');
        for (SubmissionJob.TestCase test : testCases) input.append(test.getInput()).append('\n');
        return input.toString();
    }

    public List<SubmissionJob.TestResult> mapResults(Map<String, Object> response,
                                                       List<SubmissionJob.TestCase> testCases) {
        Object statusObject = response.get("status");
        String executionError = statusDescription(statusObject);
        if (executionError != null && !"Accepted".equalsIgnoreCase(executionError)) {
            return errorResults(testCases, errorType(statusObject), executionError);
        }
        String stdout = response.get("stdout") == null ? "" : response.get("stdout").toString();
        String[] lines = stdout.replace("\r", "").split("\n", -1);
        List<SubmissionJob.TestResult> results = new ArrayList<>();
        int lineIndex = 0;
        for (SubmissionJob.TestCase test : testCases) {
            SubmissionJob.TestResult result = new SubmissionJob.TestResult();
            result.setInput(test.getInput());
            result.setExpectedOutput(test.getExpectedOutput());
            String actual = null;
            if (lineIndex < lines.length && lines[lineIndex].startsWith("CZ1:")) {
                try {
                    actual = new String(Base64.getDecoder().decode(lines[lineIndex++].substring(4)), StandardCharsets.UTF_8).trim();
                } catch (IllegalArgumentException ignored) {
                    actual = null;
                }
            }
            if (actual == null) {
                result.setPassed(false);
                result.setErrorType("INSUFFICIENT_OUTPUT");
                result.setErrorMessage("Harness output was incomplete");
            } else {
                result.setActualOutput(actual);
                result.setPassed(actual.equals(test.getExpectedOutput() == null ? "" : test.getExpectedOutput().trim()));
                if (!result.isPassed()) result.setErrorType("WRONG_ANSWER");
            }
            results.add(result);
        }
        return results;
    }

    private List<SubmissionJob.TestResult> errorResults(List<SubmissionJob.TestCase> tests, String type, String message) {
        List<SubmissionJob.TestResult> results = new ArrayList<>();
        for (SubmissionJob.TestCase test : tests) {
            SubmissionJob.TestResult result = new SubmissionJob.TestResult();
            result.setInput(test.getInput());
            result.setExpectedOutput(test.getExpectedOutput());
            result.setPassed(false);
            result.setErrorType(type);
            result.setErrorMessage(message);
            results.add(result);
        }
        return results;
    }

    private String statusDescription(Object status) {
        if (status instanceof Map<?, ?> map && map.get("description") != null) return map.get("description").toString();
        return null;
    }

    private String errorType(Object status) {
        if (status instanceof Map<?, ?> map && map.get("id") != null) {
            int id = Integer.parseInt(map.get("id").toString());
            if (id == 6) return "COMPILATION_ERROR";
            if (id == 5) return "TIME_LIMIT_EXCEEDED";
            if (id == 4) return "MEMORY_LIMIT_EXCEEDED";
            if (id >= 7 && id <= 12) return "RUNTIME_ERROR";
        }
        return "EXECUTION_ERROR";
    }

    private String javaWrapper(String body) {
        return "import java.io.*;\nimport java.nio.charset.StandardCharsets;\nimport java.util.*;\n" +
            "public class Main {\n" +
            "  static String solve(String input) {\n" + body + "\n  }\n" +
            "  public static void main(String[] args) throws Exception {\n" +
            "    BufferedReader r = new BufferedReader(new InputStreamReader(System.in));\n" +
            "    int t = Integer.parseInt(r.readLine().trim());\n" +
            "    for (int i=0;i<t;i++) { String input=r.readLine(); String out=solve(input == null ? \"\" : input);\n" +
            "      System.out.println(\"CZ1:\" + Base64.getEncoder().encodeToString(out.getBytes(StandardCharsets.UTF_8))); }\n" +
            "  }\n}\n";
    }

    private String pythonWrapper(String body) {
        return "import sys, base64\n" +
            "def solve(input_text):\n" + indent(body) + "\n" +
            "lines = sys.stdin.read().splitlines()\n" +
            "t = int(lines[0]) if lines else 0\n" +
            "for i in range(t):\n" +
            "    out = solve(lines[i + 1] if i + 1 < len(lines) else '')\n" +
            "    print('CZ1:' + base64.b64encode(str(out).encode()).decode())\n";
    }

    private String cppWrapper(String body) {
        return "#include <bits/stdc++.h>\nusing namespace std;\n" +
            "string solve(const string& input) {\n" + body + "\n}\n" +
            "string b64(const string& s) { static const string a=\"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/\"; string o; int v=0,b=-6; for(unsigned char c:s){v=(v<<8)+c;b+=8;while(b>=0){o.push_back(a[(v>>b)&63]);b-=6;}} if(b>-6)o.push_back(a[((v<<8)>>(b+8))&63]); while(o.size()%4)o.push_back('='); return o; }\n" +
            "int main(){ int t; if(!(cin>>t)) return 0; string input; getline(cin,input); for(int i=0;i<t;i++){getline(cin,input); cout<<\"CZ1:\"<<b64(solve(input))<<'\\n';} }\n";
    }

    private String indent(String body) {
        return String.join("\n", body.split("\\R", -1)).lines()
            .map(line -> "    " + line).reduce((a, b) -> a + "\n" + b).orElse("    return '';");
    }
}
