package univ.airconnect.iap.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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

import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_ATTRIBUTE;

@RestController
@RequestMapping("/api/v1/iap")
public class IapController {

    private final IapProcessingService iapProcessingService;
    private final IapQueryService iapQueryService;

    public IapController(IapProcessingService iapProcessingService,
                         IapQueryService iapQueryService) {
        this.iapProcessingService = iapProcessingService;
        this.iapQueryService = iapQueryService;
    }

    @PostMapping("/ios/transactions/verify")
    public ResponseEntity<ApiResponse<IapVerifyResponse>> verifyIos(
            @CurrentUserId Long userId,
            @Valid @RequestBody IosTransactionVerifyRequest request,
            HttpServletRequest httpRequest
    ) {
        String traceId = (String) httpRequest.getAttribute(TRACE_ID_ATTRIBUTE);
        IapVerifyResponse response = iapProcessingService.verifyIos(userId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    @PostMapping("/ios/transactions/sync")
    public ResponseEntity<ApiResponse<IapSyncResponse>> syncIos(
            @CurrentUserId Long userId,
            @Valid @RequestBody IosTransactionsSyncRequest request,
            HttpServletRequest httpRequest
    ) {
        String traceId = (String) httpRequest.getAttribute(TRACE_ID_ATTRIBUTE);
        IapSyncResponse response = iapProcessingService.syncIos(userId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    @GetMapping("/ios/transactions/{transactionId}")
    public ResponseEntity<ApiResponse<IapOrderResponse>> getIosTransaction(
            @CurrentUserId Long userId,
            @PathVariable String transactionId,
            HttpServletRequest httpRequest
    ) {
        String traceId = (String) httpRequest.getAttribute(TRACE_ID_ATTRIBUTE);
        IapOrderResponse response = iapQueryService.getAppleTransaction(userId, transactionId);
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    @PostMapping("/android/purchases/verify")
    public ResponseEntity<ApiResponse<IapVerifyResponse>> verifyAndroid(
            @CurrentUserId Long userId,
            @Valid @RequestBody AndroidPurchaseVerifyRequest request,
            HttpServletRequest httpRequest
    ) {
        String traceId = (String) httpRequest.getAttribute(TRACE_ID_ATTRIBUTE);
        IapVerifyResponse response = iapProcessingService.verifyAndroid(userId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    @PostMapping("/android/purchases/sync")
    public ResponseEntity<ApiResponse<IapSyncResponse>> syncAndroid(
            @CurrentUserId Long userId,
            @Valid @RequestBody AndroidPurchasesSyncRequest request,
            HttpServletRequest httpRequest
    ) {
        String traceId = (String) httpRequest.getAttribute(TRACE_ID_ATTRIBUTE);
        IapSyncResponse response = iapProcessingService.syncAndroid(userId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    @GetMapping("/android/purchases/{purchaseToken}")
    public ResponseEntity<ApiResponse<IapOrderResponse>> getAndroidPurchase(
            @CurrentUserId Long userId,
            @PathVariable String purchaseToken,
            HttpServletRequest httpRequest
    ) {
        String traceId = (String) httpRequest.getAttribute(TRACE_ID_ATTRIBUTE);
        IapOrderResponse response = iapQueryService.getGooglePurchase(userId, purchaseToken);
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }
}

