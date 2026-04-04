package univ.airconnect.iap.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import univ.airconnect.global.response.ApiResponse;
import univ.airconnect.global.security.resolver.CurrentUserId;
import univ.airconnect.iap.application.IapProcessingService;
import univ.airconnect.iap.application.IapQueryService;
import univ.airconnect.iap.dto.request.AndroidPurchaseVerifyRequest;
import univ.airconnect.iap.dto.request.AndroidPurchasesSyncRequest;
import univ.airconnect.iap.dto.request.IosTransactionVerifyRequest;
import univ.airconnect.iap.dto.request.IosTransactionsSyncRequest;
import univ.airconnect.iap.dto.response.IapOrderResponse;
import univ.airconnect.iap.dto.response.IapSyncResponse;
import univ.airconnect.iap.dto.response.IapVerifyResponse;
import univ.airconnect.iap.infrastructure.PayloadSecurityUtil;

import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_ATTRIBUTE;

@Slf4j
@RestController
@RequestMapping("/api/v1/iap")
@ConditionalOnProperty(value = "iap.enabled", havingValue = "true")
public class IapController {

    private final IapProcessingService iapProcessingService;
    private final IapQueryService iapQueryService;
    private final PayloadSecurityUtil payloadSecurityUtil;

    public IapController(IapProcessingService iapProcessingService,
                         IapQueryService iapQueryService,
                         PayloadSecurityUtil payloadSecurityUtil) {
        this.iapProcessingService = iapProcessingService;
        this.iapQueryService = iapQueryService;
        this.payloadSecurityUtil = payloadSecurityUtil;
    }

    @PostMapping("/ios/transactions/verify")
    public ResponseEntity<ApiResponse<IapVerifyResponse>> verifyIos(
            @CurrentUserId Long userId,
            @Valid @RequestBody IosTransactionVerifyRequest request,
            HttpServletRequest httpRequest
    ) {
        String traceId = (String) httpRequest.getAttribute(TRACE_ID_ATTRIBUTE);
        log.info("IAP iOS verify request received. traceId={}, userId={}, transactionId={}, appAccountToken={}",
                traceId, userId, request.getTransactionId(), payloadSecurityUtil.mask(request.getAppAccountToken()));
        IapVerifyResponse response = iapProcessingService.verifyIos(userId, request);
        log.info("IAP iOS verify completed. traceId={}, userId={}, transactionId={}, grantStatus={}",
                traceId, userId, response.getTransactionId(), response.getGrantStatus());
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    @PostMapping("/ios/transactions/sync")
    public ResponseEntity<ApiResponse<IapSyncResponse>> syncIos(
            @CurrentUserId Long userId,
            @Valid @RequestBody IosTransactionsSyncRequest request,
            HttpServletRequest httpRequest
    ) {
        String traceId = (String) httpRequest.getAttribute(TRACE_ID_ATTRIBUTE);
        log.info("IAP iOS sync request received. traceId={}, userId={}, itemCount={}",
                traceId, userId, request.getTransactions().size());
        IapSyncResponse response = iapProcessingService.syncIos(userId, request);
        log.info("IAP iOS sync completed. traceId={}, userId={}, total={}, success={}, failure={}",
                traceId, userId, response.getTotal(), response.getSuccessCount(), response.getFailureCount());
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    @GetMapping("/ios/transactions/{transactionId}")
    public ResponseEntity<ApiResponse<IapOrderResponse>> getIosTransaction(
            @CurrentUserId Long userId,
            @PathVariable String transactionId,
            HttpServletRequest httpRequest
    ) {
        String traceId = (String) httpRequest.getAttribute(TRACE_ID_ATTRIBUTE);
        log.info("IAP iOS query request received. traceId={}, userId={}, transactionId={}",
                traceId, userId, transactionId);
        IapOrderResponse response = iapQueryService.getAppleTransaction(userId, transactionId);
        log.info("IAP iOS query completed. traceId={}, userId={}, orderId={}, status={}",
                traceId, userId, response.getId(), response.getStatus());
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    @PostMapping("/android/purchases/verify")
    public ResponseEntity<ApiResponse<IapVerifyResponse>> verifyAndroid(
            @CurrentUserId Long userId,
            @Valid @RequestBody AndroidPurchaseVerifyRequest request,
            HttpServletRequest httpRequest
    ) {
        String traceId = (String) httpRequest.getAttribute(TRACE_ID_ATTRIBUTE);
        log.info("IAP Android verify request received. traceId={}, userId={}, purchaseToken={}, orderId={}",
                traceId, userId, payloadSecurityUtil.mask(request.getPurchaseToken()), request.getOrderId());
        IapVerifyResponse response = iapProcessingService.verifyAndroid(userId, request);
        log.info("IAP Android verify completed. traceId={}, userId={}, purchaseToken={}, grantStatus={}",
                traceId, userId, payloadSecurityUtil.mask(response.getPurchaseToken()), response.getGrantStatus());
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    @PostMapping("/android/purchases/sync")
    public ResponseEntity<ApiResponse<IapSyncResponse>> syncAndroid(
            @CurrentUserId Long userId,
            @Valid @RequestBody AndroidPurchasesSyncRequest request,
            HttpServletRequest httpRequest
    ) {
        String traceId = (String) httpRequest.getAttribute(TRACE_ID_ATTRIBUTE);
        log.info("IAP Android sync request received. traceId={}, userId={}, itemCount={}",
                traceId, userId, request.getPurchases().size());
        IapSyncResponse response = iapProcessingService.syncAndroid(userId, request);
        log.info("IAP Android sync completed. traceId={}, userId={}, total={}, success={}, failure={}",
                traceId, userId, response.getTotal(), response.getSuccessCount(), response.getFailureCount());
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    @GetMapping("/android/purchases/{purchaseToken}")
    public ResponseEntity<ApiResponse<IapOrderResponse>> getAndroidPurchase(
            @CurrentUserId Long userId,
            @PathVariable String purchaseToken,
            HttpServletRequest httpRequest
    ) {
        String traceId = (String) httpRequest.getAttribute(TRACE_ID_ATTRIBUTE);
        log.info("IAP Android query request received. traceId={}, userId={}, purchaseToken={}",
                traceId, userId, payloadSecurityUtil.mask(purchaseToken));
        IapOrderResponse response = iapQueryService.getGooglePurchase(userId, purchaseToken);
        log.info("IAP Android query completed. traceId={}, userId={}, orderId={}, status={}",
                traceId, userId, response.getId(), response.getStatus());
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }
}

