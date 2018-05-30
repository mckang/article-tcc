package ymyoo.stock.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ymyoo.stock.Status;
import ymyoo.stock.adapter.messaging.StockAdjustmentChannelAdapter;
import ymyoo.stock.dto.StockAdjustment;
import ymyoo.stock.entity.ReservedStock;
import ymyoo.stock.entity.Stock;
import ymyoo.stock.repository.ReservedStockRepository;
import ymyoo.stock.repository.StockRepository;
import ymyoo.stock.service.StockService;

import java.time.LocalDateTime;

@Service
public class StockServiceImpl implements StockService {
    private static final Logger log = LoggerFactory.getLogger(StockServiceImpl.class);

    private ReservedStockRepository reservedStockRepository;
    private StockRepository stockRepository;

    private StockAdjustmentChannelAdapter stockAdjustmentChannelAdapter;

    @Autowired
    public void setReservedStockRepository(ReservedStockRepository reservedStockRepository) {
        this.reservedStockRepository = reservedStockRepository;
    }

    @Autowired
    public void setStockRepository(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    @Autowired
    public void setStockAdjustmentChannelAdapter(StockAdjustmentChannelAdapter stockAdjustmentChannelAdapter) {
        this.stockAdjustmentChannelAdapter = stockAdjustmentChannelAdapter;
    }

    @Override
    public ReservedStock reserveStock(final StockAdjustment stockAdjustment) {
        ReservedStock reservedStock = new ReservedStock(stockAdjustment);

        reservedStockRepository.save(reservedStock);

        log.info("Reserved Stock :" + reservedStock.getId());
        return reservedStock;
    }

    @Transactional
    @Override
    public void confirmStock(Long id, LocalDateTime confirmedTime) {
        ReservedStock reservedStock = reservedStockRepository.getOne(id);

        if(reservedStock == null) {
            throw new IllegalArgumentException("Not found");
        }

        reservedStock.validate(confirmedTime);

        // ReservedStock 상태를 Confirm 으로 변경
        reservedStock.setStatus(Status.CONFIRMED);
        reservedStockRepository.save(reservedStock);

        // Messaging Queue 로 전송
        stockAdjustmentChannelAdapter.publish(reservedStock.getResources());

        log.info("Confirm Stock :" + id);
    }

    @Transactional
    @Override
    public void decreaseStock(final String productId, final Long qty) {
        Stock stock = stockRepository.findByProductId(productId);
        stock.decrease(qty);

        stockRepository.save(stock);

        log.info(String.format("Stock decreased ..[productId : %s][qty  : %d]", productId, qty));
    }

    @Transactional
    @Override
    public void cancelStock(final Long id) {
        ReservedStock reservedStock = reservedStockRepository.getOne(id);

        reservedStock.setStatus(Status.CANCEL);
        reservedStockRepository.save(reservedStock);

        log.info("Cancel Stock :" + id);
    }
}
