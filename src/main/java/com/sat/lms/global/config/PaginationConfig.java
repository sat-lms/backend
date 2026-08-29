package com.sat.lms.global.config;

import com.sat.lms.global.exception.BusinessException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.HttpStatus;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class PaginationConfig implements WebMvcConfigurer {
    private static final String INVALID_PAGINATION_MESSAGE = "페이지와 크기 값이 올바르지 않습니다.";

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        StrictPageableResolver resolver = new StrictPageableResolver();
        resolver.setFallbackPageable(PageRequest.of(0, 20));
        resolvers.add(resolver);
    }

    private static final class StrictPageableResolver extends PageableHandlerMethodArgumentResolver {
        @Override
        public Pageable resolveArgument(MethodParameter methodParameter,
                                        ModelAndViewContainer mavContainer,
                                        NativeWebRequest webRequest,
                                        org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
            validateIntegerParameter(webRequest, "page", 0);
            validateIntegerParameter(webRequest, "size", 1);
            return super.resolveArgument(methodParameter, mavContainer, webRequest, binderFactory);
        }

        private void validateIntegerParameter(NativeWebRequest request, String name, int minimum) {
            String[] values = request.getParameterValues(name);
            if (values == null) return;
            if (values.length != 1) throw invalidPagination();
            try {
                if (Integer.parseInt(values[0]) < minimum) throw invalidPagination();
            } catch (NumberFormatException exception) {
                throw invalidPagination();
            }
        }

        private BusinessException invalidPagination() {
            return new BusinessException(HttpStatus.BAD_REQUEST, INVALID_PAGINATION_MESSAGE);
        }
    }
}
