package com.titanium.claim.infrastructure.adapter.customer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.claim.port.customer.CustomerServicePort.CustomerInfo;
import com.titanium.customer.api.CustomerApi;
import com.titanium.customer.api.response.CustomerResponse;
import com.titanium.metadata.enums.customer.CustomerEnum;

/**
 * 客户服务 Adapter 入站契约测试（受益人身份核验取证链路，CLAIM-4）
 * <p>
 * 锁死三条判据：① 命中时把下游契约翻译为领域摘要（客户ID/姓名/状态码），领域不触碰对端 api 类型；
 * ② <b>对端返回 null 表示「客户不存在」，适配器必须原样返回 null 而不抛异常</b>——调用方须能区分
 * 「不存在」与「状态无效」两种拒绝理由，适配器提前抛异常会把两者压成一个错误；
 * ③ 对端字段缺失（status / customerId 为 null）时降级为 null/回落入参，不得 NPE——对端契约演进
 * 不该把核验链路炸掉。
 * </p>
 */
class CustomerServiceAdapterTest {

    private static final String CUSTOMER_ID = "C-1";
    private static final String TENANT_ID   = "T-1";

    private CustomerApi           customerApi;
    private CustomerServiceAdapter adapter;

    @BeforeEach
    void setUp() {
        customerApi = mock(CustomerApi.class);
        adapter = new CustomerServiceAdapter(customerApi);
    }

    @Test
    @DisplayName("命中客户：翻译为客户摘要（ID/姓名/状态码），租户ID透传")
    void shouldTranslateCustomerResponse() {
        CustomerResponse response = new CustomerResponse();
        response.setCustomerId(CUSTOMER_ID);
        response.setFullName("张三");
        response.setStatus(CustomerEnum.CustomerStatus.ACTIVE);
        when(customerApi.getCustomer(CUSTOMER_ID, TENANT_ID)).thenReturn(response);

        CustomerInfo info = adapter.getCustomer(CUSTOMER_ID, TENANT_ID);

        assertEquals(CUSTOMER_ID, info.customerId());
        assertEquals("张三", info.name());
        assertEquals(CustomerEnum.CustomerStatus.ACTIVE.getCode(), info.statusCode());
        verify(customerApi).getCustomer(CUSTOMER_ID, TENANT_ID);
    }

    @Test
    @DisplayName("对端返回 null（客户不存在）：原样返回 null，不抛异常")
    void shouldReturnNullWhenCustomerAbsent() {
        when(customerApi.getCustomer(CUSTOMER_ID, TENANT_ID)).thenReturn(null);

        assertNull(adapter.getCustomer(CUSTOMER_ID, TENANT_ID));
    }

    @Test
    @DisplayName("对端字段缺失：status 为 null 时状态码降级为 null，不 NPE")
    void shouldDegradeWhenStatusMissing() {
        CustomerResponse response = new CustomerResponse();
        response.setCustomerId(CUSTOMER_ID);
        response.setFullName("张三");
        when(customerApi.getCustomer(CUSTOMER_ID, TENANT_ID)).thenReturn(response);

        assertNull(adapter.getCustomer(CUSTOMER_ID, TENANT_ID).statusCode());
    }

    @Test
    @DisplayName("对端未回填客户ID：回落入参 ID，保证核验对象可追溯")
    void shouldFallbackToRequestedCustomerId() {
        CustomerResponse response = new CustomerResponse();
        response.setFullName("张三");
        response.setStatus(CustomerEnum.CustomerStatus.ACTIVE);
        when(customerApi.getCustomer(CUSTOMER_ID, TENANT_ID)).thenReturn(response);

        assertEquals(CUSTOMER_ID, adapter.getCustomer(CUSTOMER_ID, TENANT_ID).customerId());
    }
}
