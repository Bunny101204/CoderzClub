package com.coderzclub.controller;

import com.coderzclub.service.ContestAccessService;
import com.coderzclub.service.ProblemAccessService;
import com.coderzclub.service.ProblemReadCache;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProblemCatalogControllerCacheTest {

    @Test
    void defaultListCacheHitDoesNotTouchMongo() throws Exception {
        ProblemReadCache cache = mock(ProblemReadCache.class);
        when(cache.listCacheable(any(), any(), any(), any(), anyBoolean(), anyInt(), anyInt())).thenReturn(true);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("problems", List.of());
        body.put("totalItems", 3);
        body.put("totalPages", 1);
        when(cache.readList(eq(false), eq(0), eq(20), isNull())).thenReturn(body);

        ProblemController controller = new ProblemController();
        ReflectionTestUtils.setField(controller, "problemReadCache", cache);
        ReflectionTestUtils.setField(controller, "problemAccessService",
            new ProblemAccessService(mock(ContestAccessService.class)));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

        mockMvc.perform(get("/api/problems"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(3))
            .andExpect(jsonPath("$.problems.length()").value(0));
    }
}
