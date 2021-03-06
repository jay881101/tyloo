package io.tyloo.repository;


import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.tyloo.ConcurrentTransactionException;
import io.tyloo.OptimisticLockException;
import io.tyloo.Transaction;
import io.tyloo.TransactionRepository;
import io.tyloo.api.TransactionXid;

import javax.transaction.xa.Xid;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/*
 *
 * 缓存事务库
 *
 * @Author:Zh1Cheung zh1cheunglq@gmail.com
 * @Date: 19:08 2019/5/12
 *
 */
public abstract class CachableTransactionRepository implements TransactionRepository {

    /**
     * 到期时间(以秒为单位)
     */
    private int expireDuration = 120;
    /**
     * 事务日志记录缓存<Xid, Transaction>
     */
    private final Cache<Xid, Transaction> transactionXidTylooTransactionCache;

    public CachableTransactionRepository() {
        transactionXidTylooTransactionCache = CacheBuilder.newBuilder().expireAfterAccess(expireDuration, TimeUnit.SECONDS).maximumSize(1000).build();
    }

    /**
     * 创建事务日志记录
     */
    @Override
    public int create(Transaction transaction) throws CloneNotSupportedException {
        int result = doCreate(transaction);
        if (result > 0) {
            putToCache(transaction);
        } else {
            throw new ConcurrentTransactionException("transaction xid duplicated. xid:" + transaction.getXid().toString());
        }

        return result;
    }

    /**
     * 更新事务日志记录
     */
    @Override
    public int update(Transaction transaction) {
        int result = 0;

        try {
            result = doUpdate(transaction);
            if (result > 0) {
                putToCache(transaction);
            } else {
                throw new OptimisticLockException();
            }
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        } finally {
            if (result <= 0) {
                removeFromCache(transaction);
            }
        }

        return result;
    }

    /**
     * 删除事务日志记录
     */
    @Override
    public int delete(Transaction transaction) {
        int result = 0;

        try {
            result = doDelete(transaction);

        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        } finally {
            removeFromCache(transaction);
        }
        return result;
    }

    /**
     * 根据xid查找事务日志记录.
     *
     * @param transactionXid
     * @return
     */
    @Override
    public Transaction findByXid(TransactionXid transactionXid) {
        Transaction transaction = findFromCache(transactionXid);

        if (transaction == null) {
            transaction = doFindOne(transactionXid);
            if (transaction != null) {
                putToCache(transaction);
            }
        }

        return transaction;
    }

    /**
     * 找出所有未处理事务日志（从某一时间点开始）.
     *
     * @return
     */
    @Override
    public List<Transaction> findAllUnmodifiedSince(Date date) {

        List<Transaction> transactions = doFindAllUnmodifiedSince(date);
        for (Transaction transaction : transactions) {
            putToCache(transaction);
        }
        return transactions;
    }

    /**
     * 放入缓存.
     *
     * @param transaction
     */
    protected void putToCache(Transaction transaction) {
        transactionXidTylooTransactionCache.put(transaction.getXid(), transaction);
    }

    /**
     * 从缓存中删除.
     *
     * @param transaction
     */
    protected void removeFromCache(Transaction transaction) {
        transactionXidTylooTransactionCache.invalidate(transaction.getXid());
    }

    /**
     * 从缓存中查找.
     *
     * @param transactionXid
     * @return
     */
    protected Transaction findFromCache(TransactionXid transactionXid) {
        return transactionXidTylooTransactionCache.getIfPresent(transactionXid);
    }

    public void setExpireDuration(int durationInSeconds) {
        this.expireDuration = durationInSeconds;
    }

    /**
     * 创建事务日志记录
     *
     * @param transaction
     * @return
     */
    protected abstract int doCreate(Transaction transaction) throws CloneNotSupportedException;

    protected abstract int doUpdate(Transaction transaction) throws CloneNotSupportedException;

    protected abstract int doDelete(Transaction transaction) throws CloneNotSupportedException;

    protected abstract Transaction doFindOne(Xid xid);

    protected abstract List<Transaction> doFindAllUnmodifiedSince(Date date);
}
