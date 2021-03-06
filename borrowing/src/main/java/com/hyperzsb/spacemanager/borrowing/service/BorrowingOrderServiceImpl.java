package com.hyperzsb.spacemanager.borrowing.service;

import com.hyperzsb.spacemanager.borrowing.domain.Academy;
import com.hyperzsb.spacemanager.borrowing.domain.Borrower;
import com.hyperzsb.spacemanager.borrowing.domain.BorrowingOrder;
import com.hyperzsb.spacemanager.borrowing.domain.Room;
import com.hyperzsb.spacemanager.borrowing.enumeration.OrderStatus;
import com.hyperzsb.spacemanager.borrowing.exception.BorrowingOrderConflictException;
import com.hyperzsb.spacemanager.borrowing.exception.BorrowingOrderDaoException;
import com.hyperzsb.spacemanager.borrowing.repository.AcademyRepository;
import com.hyperzsb.spacemanager.borrowing.repository.BorrowerRepository;
import com.hyperzsb.spacemanager.borrowing.repository.BorrowingOrderRepository;
import com.hyperzsb.spacemanager.borrowing.repository.RoomRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class BorrowingOrderServiceImpl implements BorrowingOrderService {

    private Logger logger = LogManager.getLogger(BorrowingOrderServiceImpl.class);

    @Autowired
    private BorrowingOrderRepository borrowingOrderRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BorrowerRepository borrowerRepository;

    @Autowired
    private AcademyRepository academyRepository;

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public BorrowingOrder addOrder(BorrowingOrder borrowingOrder) throws BorrowingOrderDaoException {
        try {
            logger.info("Start service.");
            Borrower borrower = null;
            logger.info("Finding academy...");
            Academy academy = academyRepository.findAcademyByName(borrowingOrder.getBorrower().getAcademy().getName());
            if (academy != null) {
                logger.info("Found academy. Academy is: " + academy.toCustomString());
                borrower = new Borrower(borrowingOrder.getBorrower().getId(),
                        borrowingOrder.getBorrower().getName(), academy);
            } else {
                logger.info("Not found academy.");
                academy = new Academy(borrowingOrder.getBorrower().getAcademy().getName());
                academyRepository.save(academy);
                academy = academyRepository.findAcademyByName(borrowingOrder.getBorrower().getAcademy().getName());
                borrower = new Borrower(borrowingOrder.getBorrower().getId(),
                        borrowingOrder.getBorrower().getName(), academy);
            }
            borrowingOrder.setBorrower(borrower);
            logger.info("Borrower set: " + borrower.toCustomString());
            Room room = roomRepository.findRoomByName(borrowingOrder.getRoom().getName());
            logger.info("Found room: " + room.toCustomString());
            borrowingOrder.setRoom(room);
            List<BorrowingOrder> borrowingOrderList =
                    borrowingOrderRepository.findBorrowingOrdersByRoomName(borrowingOrder.getRoom().getName());
            for (BorrowingOrder bO : borrowingOrderList) {
                if ((bO.getStartTime().compareTo(borrowingOrder.getEndTime()) < 0
                        && bO.getEndTime().compareTo(borrowingOrder.getStartTime()) > 0)
                        || (bO.getEndTime().compareTo(borrowingOrder.getStartTime()) > 0
                        && bO.getStartTime().compareTo(borrowingOrder.getEndTime()) < 0)) {
                    throw new BorrowingOrderConflictException(bO);
                }
            }
            logger.info("Before save");
            borrowerRepository.save(borrower);
            logger.info("Borrower saved");
            borrowingOrderRepository.save(borrowingOrder);
            logger.info("Borrowing order saved");
            return borrowingOrder;
        } catch (Exception e) {
            if (e instanceof BorrowingOrderConflictException) {
                throw e;
            } else {
                throw new BorrowingOrderDaoException("Unable to add new order");
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<BorrowingOrder> getOrder() {
        return borrowingOrderRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public BorrowingOrder getOrderByOrderId(Integer id) throws BorrowingOrderDaoException {
        Optional<BorrowingOrder> borrowingOrder = borrowingOrderRepository.findById(id);
        if (borrowingOrder.isPresent())
            return borrowingOrder.get();
        else
            throw new BorrowingOrderDaoException("No such order");
    }

    @Override
    @Transactional(readOnly = true)
    public List<BorrowingOrder> getOrderByBorrowerId(Integer id) {
        return borrowingOrderRepository.findBorrowingOrdersByBorrowerId(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BorrowingOrder> getOrderByBorrowerName(String name) {
        return borrowingOrderRepository.findBorrowingOrdersByBorrowerName(name);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BorrowingOrder> getOrderByRoomId(Integer id) {
        return borrowingOrderRepository.findBorrowingOrdersByRoomId(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BorrowingOrder> getOrderByRoomName(String name) {
        return borrowingOrderRepository.findBorrowingOrdersByRoomName(name);
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public BorrowingOrder updateOrderByOrderId(Integer id, BorrowingOrder newBorrowingOrder) throws BorrowingOrderDaoException {
        Optional<BorrowingOrder> oldBorrowingOrder = borrowingOrderRepository.findById(id);
        if (oldBorrowingOrder.isPresent()) {
            Academy academy = academyRepository.findAcademyByName(newBorrowingOrder.getBorrower().getAcademy().getName());
            Borrower borrower = new Borrower(newBorrowingOrder.getBorrower().getId(),
                    newBorrowingOrder.getBorrower().getName(), academy);
            newBorrowingOrder.setBorrower(borrower);
            Room room = roomRepository.findRoomByName(newBorrowingOrder.getRoom().getName());
            newBorrowingOrder.setRoom(room);
            List<BorrowingOrder> borrowingOrderList =
                    borrowingOrderRepository.findBorrowingOrdersByRoomName(newBorrowingOrder.getRoom().getName());
            for (BorrowingOrder bO : borrowingOrderList) {
                if (bO.getStartTime().compareTo(newBorrowingOrder.getEndTime()) < 0
                        || bO.getEndTime().compareTo(newBorrowingOrder.getStartTime()) > 0) {
                    throw new BorrowingOrderConflictException(bO);
                }
            }
            if (!oldBorrowingOrder.get().getBorrower().getId().equals(borrower.getId()) ||
                    !oldBorrowingOrder.get().getBorrower().getName().equals(borrower.getName()) ||
                    !oldBorrowingOrder.get().getBorrower().getAcademy().getId().equals(borrower.getAcademy().getId())) {
                borrowerRepository.deleteById(oldBorrowingOrder.get().getBorrower().getId());
                borrowerRepository.save(borrower);
            }
            newBorrowingOrder.setId(oldBorrowingOrder.get().getId());
            borrowingOrderRepository.save(newBorrowingOrder);
            return newBorrowingOrder;
        } else {
            throw new BorrowingOrderDaoException("No such order");
        }
    }

    @Override
    public BorrowingOrder updateOrderStatusByOrderId(Integer id, Integer orderStatusValue) throws BorrowingOrderDaoException {
        Optional<BorrowingOrder> borrowingOrder = borrowingOrderRepository.findById(id);
        if (borrowingOrder.isPresent()) {
            BorrowingOrder newBorrowingOrder = borrowingOrder.get();
            newBorrowingOrder.setOrderStatus(OrderStatus.getOrderStatusByValue(orderStatusValue));
            borrowingOrderRepository.save(newBorrowingOrder);
            return newBorrowingOrder;
        } else {
            throw new BorrowingOrderDaoException("No such order");
        }
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public BorrowingOrder deleteOrderByOrderId(Integer id) throws BorrowingOrderDaoException {
        Optional<BorrowingOrder> borrowingOrder = borrowingOrderRepository.findById(id);
        if (borrowingOrder.isPresent()) {
            borrowingOrderRepository.deleteById(id);
            return borrowingOrder.get();
        } else {
            throw new BorrowingOrderDaoException("No such order");
        }
    }

}
