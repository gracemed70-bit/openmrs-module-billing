package org.openmrs.module.yourmodule.billing;  
  
import lombok.extern.slf4j.Slf4j;  
import org.openmrs.Order;  
import org.openmrs.module.billing.api.billing.BillingResult;  
import org.openmrs.module.billing.api.billing.impl.TestOrderBillingStrategy;  
  
/**  
 * Bills test orders like the default strategy, but does NOT void the bill line  
 * item when the order was discontinued because it was fulfilled/completed.  
 */  
@Slf4j  
public class CompletionAwareTestOrderBillingStrategy extends TestOrderBillingStrategy {  
  
	@Override  
	protected BillingResult handleDiscontinuedOrder(Order order) {  
		Order previous = order.getPreviousOrder();  
		if (previous != null && previous.getFulfillerStatus() == Order.FulfillerStatus.COMPLETED) {  
			log.info("Order {} completed via discontinue - keeping bill line item", previous.getUuid());  
			return BillingResult.skipped("Order fulfilled - keeping line item");  
		}  
		// genuine cancellation: keep the default behaviour (void the line item)  
		return super.handleDiscontinuedOrder(order);  
	}  
  
	@Override  
	public int getOrder() {  
		// must be lower than Ordered.LOWEST_PRECEDENCE so this strategy is  
		// consulted before the shipped TestOrderBillingStrategy  
		return 0;  
	}  
}
