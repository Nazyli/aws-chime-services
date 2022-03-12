package com.nazyli.awschime.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@NoArgsConstructor
@Data
public class ContentDto {
    private String url;
    private Date dateContent;
    private long start;
    private long end;
    private long next;
    private Double duration;
    private Object inString;
}
