package eventcenter.api.tx;

import eventcenter.api.EventSourceBase;

/**
 * resume uncommitted transaction handler, it would call back by {@link TransactionalSupport#resumeTxn(ResumeTxnHandler)}
 *
 * @author liumingjian
 * @date 2016/12/29
 */
public interface ResumeTxnHandler {

    void resume(EventTxnStatus status, EventSourceBase event);
}
