package com.titanium.claim.valueobject;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 查勘记录值对象（车险/寿险调查阶段）
 * <p>
 * 记录理赔查勘阶段的现场勘查结果：查勘员、查勘报告、现场照片、查勘结论。 车险出险需现场查勘取证，寿险/重疾的大额案件需委托调查。
 * </p>
 *
 * @param surveyorId     查勘员ID
 * @param surveyReport   查勘报告
 * @param photos         现场照片URL列表
 * @param conclusion     查勘结论
 * @param surveyedAt     查勘时间
 *
 * @author wei.sun
 * @since 2026/6/23
 */
public record Survey(
        String surveyorId,
        String surveyReport,
        List<String> photos,
        String conclusion,
        LocalDateTime surveyedAt) {

    public Survey {
        photos = photos == null ? List.of() : List.copyOf(photos);
    }
}
