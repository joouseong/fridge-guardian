package com.fridgeguardian.recommendation;

/** 부족한 재료를 외부 쇼핑몰에서 검색하는 링크. 서버는 링크를 열거나 추적하지 않습니다. */
public record ShoppingLink(String ingredient, String mall, String url) { }
