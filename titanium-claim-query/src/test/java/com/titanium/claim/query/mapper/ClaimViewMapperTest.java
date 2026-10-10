package com.titanium.claim.query.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.titanium.claim.event.ClaimCreatedEvent;
import com.titanium.claim.query.view.ClaimView;
import com.titanium.claim.valueobject.ClaimAmount;
import com.titanium.claim.valueobject.ClaimId;
import com.titanium.claim.valueobject.CustomerId;
import com.titanium.claim.valueobject.PolicyId;
import com.titanium.metadata.enums.claim.ClaimEnum;

/**
 * 理赔创建投影映射器测试（{@link ClaimViewMapper#applyCreated}）。
 * <p>
 * 重点守护 G11/g11-03 新增的**标的键**（{@code subject_id}/{@code vin}）：映射器
 * {@code unmappedTargetPolicy} 为 {@code IGNORE}，映射源写错时 MapStruct 只会**静默漏字段**、
 * 不报编译错——读模型该列将永远为空，`按车辆检索历史赔案` 便恒查无记录，而 NCD 反算会把
 * 「查不到」误判成「无赔款」。故直接断言生成实现（{@link ClaimViewMapperImpl}）的真实映射结果。
 * </p>
 */
class ClaimViewMapperTest {

    private final ClaimViewMapper mapper = new ClaimViewMapperImpl();

    /** 事件携带标的键：subjectId/vin 必须落读模型（否则按车辆检索恒空） */
    @Test
    void mapsSubjectKeysFromCreatedEvent() {
        ClaimView view = new ClaimView();

        mapper.applyCreated(view, createdEvent("SUBJ-1", "VIN-1"));

        assertEquals("SUBJ-1", view.getSubjectId());
        assertEquals("VIN-1", view.getVin());
    }

    /**
     * 事件缺标的键（存量赔案/非车险）：落 null 而非清空既有值。
     * <p>
     * {@link org.mapstruct.NullValuePropertyMappingStrategy#IGNORE} 保证 upsert 语义——历史事件
     * 回放不得抹掉已投影的标的键。
     * </p>
     */
    @Test
    void keepsSubjectKeysUntouchedWhenEventCarriesNone() {
        ClaimView view = new ClaimView();
        view.setSubjectId("SUBJ-KEPT");
        view.setVin("VIN-KEPT");

        mapper.applyCreated(view, createdEvent(null, null));

        assertEquals("SUBJ-KEPT", view.getSubjectId());
        assertEquals("VIN-KEPT", view.getVin());
    }

    /** 新建读模型且事件无标的键：两列均为 null（存量赔案按车辆检索不到，属预期） */
    @Test
    void leavesSubjectKeysNullForLegacyEvent() {
        ClaimView view = new ClaimView();

        mapper.applyCreated(view, createdEvent(null, null));

        assertNull(view.getSubjectId());
        assertNull(view.getVin());
    }

    private ClaimCreatedEvent createdEvent(String subjectId, String vin) {
        return new ClaimCreatedEvent(ClaimId.of("CLAIM-M-1"), CustomerId.of("C-1"), PolicyId.of("P-1"), "CLM-001",
                ClaimEnum.ClaimType.PROPERTY, LocalDateTime.now().minusDays(1), "车辆碰撞", ClaimAmount.of("10000"),
                LocalDateTime.now().minusDays(1), "T-1", subjectId, vin);
    }
}
