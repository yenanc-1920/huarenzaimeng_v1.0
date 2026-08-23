package com.huarenzaimeng.api;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class H5EntryControllerTest {
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new H5EntryController()).build();

    @Test void servesFixedH5EntryPaths() throws Exception {
        mvc.perform(get("/h5")).andExpect(status().isOk()).andExpect(forwardedUrl("/h5/index.html"));
        mvc.perform(get("/h5/")).andExpect(status().isOk()).andExpect(forwardedUrl("/h5/index.html"));
    }
}
