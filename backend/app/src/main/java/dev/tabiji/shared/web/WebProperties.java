package dev.tabiji.shared.web;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/** HTTP-level limits that apply to every request. */
@ConfigurationProperties("app.web")
public record WebProperties(@DefaultValue("64KB") DataSize maxRequestBody) {}
