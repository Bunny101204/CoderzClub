package com.coderzclub.config;

import com.coderzclub.model.ExecutionMode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.convert.ReadingConverter;

@Configuration
public class ExecutionModeMongoConfig {
    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return MongoCustomConversions.create(adapter -> adapter.registerConverter(new ExecutionModeReadConverter()));
    }

    @ReadingConverter
    static class ExecutionModeReadConverter implements Converter<String, ExecutionMode> {
        @Override
        public ExecutionMode convert(String source) {
            return ExecutionMode.fromValue(source);
        }
    }
}