package com.fridgeguardian.ingredient;

/** 수량 단위와 화면·AI 프롬프트에 쓰는 한국어 표기입니다. kg↔g, L↔ml 외의 환산은 하지 않습니다. */
public enum Unit {
    PIECE("개"), PACK("팩"), BOTTLE("병"), BAG("봉"), GRAM("g"), KILOGRAM("kg"), MILLILITER("ml"), LITER("L"),
    BLOCK("모"), BUNCH("단");

    private final String label;
    Unit(String label) { this.label = label; }
    public String label() { return label; }
}
