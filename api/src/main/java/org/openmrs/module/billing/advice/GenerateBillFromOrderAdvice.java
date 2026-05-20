package org.openmrs.module.billing.advice;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.openmrs.Patient;
import org.openmrs.Provider;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.module.billing.api.BillService;
import org.openmrs.module.billing.api.CashierItemPriceService;
import org.openmrs.module.billing.api.CashPointService;
import org.openmrs.module.billing.api.base.PagingInfo;
import org.openmrs.module.billing.api.model.Bill;
import org.openmrs.module.billing.api.model.BillLineItem;
import org.openmrs.module.billing.api.model.BillStatus;
import org.openmrs.module.billing.api.model.CashPoint;
import org.openmrs.module.billing.api.model.CashierItemPrice;
import org.openmrs.module.billing.api.model.StockItem;
import org.openmrs.module.billing.api.search.BillSearch;
import org.openmrs.module.billableservices.api.IBillableServiceService;
import org.openmrs.module.billableservices.api.model.BillableService;
import org.openmrs.order.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.MethodBeforeAdvice;

public class GenerateBillFromOrderAdvice implements MethodBeforeAdvice {
	
	private static final Logger LOG = LoggerFactory.getLogger(GenerateBillFromOrderAdvice.class);
	
	private BillService billService;
	private CashierItemPriceService priceService;
	private CashPointService cashPointService;
	private IBillableServiceService billableServiceService;
	
	@Override
	public void before(java.lang.reflect.Method method, Object[] args, Object target) throws Throwable {
		Order order = (Order) args[0];
		
		if (order.getOrderReasonConcept() != null) {
			Patient patient = order.getPatient();
			StockItem stockitem = null;
			BillableService service = null;
			
			String drugUUID = "";
			if (order.getDrug() != null) {
				drugUUID = order.getDrug().getUuid();
				// Get stock item from drug UUID
			}
			
			String serviceUUID = "";
			if (order.getOrderType() != null && "billing".equals(order.getOrderType().getJavaClassName())) {
				serviceUUID = order.getConceptUuid();
				// Get billable service from service UUID
			}
			
			// Determine quantity and date
			Integer quantity = 1;
			Date orderDate = order.getDateCreated();
			
			// Get cashier UUID
			String cashierUUID = "";
			if (Context.getAuthenticatedUser() != null) {
				cashierUUID = Context.getAuthenticatedUser().getUuid();
			}
			
			addBillItemToBill(order, patient, cashierUUID, stockitem, service, quantity, orderDate, BillStatus.PENDING);
		}
	}
	
	public void addBillItemToBill(Order order, Patient patient, String cashierUUID, StockItem stockitem,
	        BillableService service, Integer quantity, Date orderDate, BillStatus lineItemStatus) {
		try {
			// Search for an existing PENDING bill for this patient
			BillSearch billSearch = new BillSearch();
			billSearch.setPatientUuid(patient.getUuid());
			billSearch.setStatuses(Collections.singletonList(BillStatus.PENDING));
			List<Bill> existingBills = billService.getBills(billSearch, null);
			
			Bill activeBill;
			if (!existingBills.isEmpty()) {
				activeBill = existingBills.get(0);  // reuse the existing pending bill
			} else {
				activeBill = new Bill();             // create a new bill only when none exists
				activeBill.setPatient(patient);
				activeBill.setStatus(BillStatus.PENDING);
				
				User user = Context.getAuthenticatedUser();
				List<Provider> providers = new ArrayList<>(Context.getProviderService().getProvidersByPerson(user.getPerson()));
				if (providers.isEmpty()) {
					LOG.error("User is not a provider");
					return;
				}
				activeBill.setCashier(providers.get(0));
				List<CashPoint> cashPoints = cashPointService.getAll();
				activeBill.setCashPoint(cashPoints.get(0));
			}
			
			// Build the line item
			BillLineItem billLineItem = new BillLineItem();
			List<CashierItemPrice> itemPrices = new ArrayList<>();
			if (stockitem != null) {
				billLineItem.setItem(stockitem);
				itemPrices = priceService.getItemPrice(stockitem);
			} else if (service != null) {
				billLineItem.setBillableService(service);
				itemPrices = priceService.getServicePrice(service);
			}
			
			if (!itemPrices.isEmpty()) {
				billLineItem.setPrice(itemPrices.get(0).getPrice());
			} else {
				if (stockitem != null && stockitem.getPurchasePrice() != null) {
					billLineItem.setPrice(stockitem.getPurchasePrice());
				} else {
					billLineItem.setPrice(new BigDecimal(0.0));
				}
			}
			billLineItem.setQuantity(quantity);
			billLineItem.setPaymentStatus(lineItemStatus);
			billLineItem.setLineItemOrder(activeBill.getLineItems() != null ? activeBill.getLineItems().size() : 0);
			billLineItem.setOrder(order);
			
			activeBill.addLineItem(billLineItem);
			activeBill.setStatus(BillStatus.PENDING);
			billService.saveBill(activeBill);
		}
		catch (Exception ex) {
			LOG.error("Error sending the bill item: " + ex.getMessage(), ex);
		}
	}
	
	public void setBillService(BillService billService) {
		this.billService = billService;
	}
	
	public void setPriceService(CashierItemPriceService priceService) {
		this.priceService = priceService;
	}
	
	public void setCashPointService(CashPointService cashPointService) {
		this.cashPointService = cashPointService;
	}
	
	public void setBillableServiceService(IBillableServiceService billableServiceService) {
		this.billableServiceService = billableServiceService;
	}
}
