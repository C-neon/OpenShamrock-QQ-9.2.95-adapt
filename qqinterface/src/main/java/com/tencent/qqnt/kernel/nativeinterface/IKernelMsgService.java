package com.tencent.qqnt.kernel.nativeinterface;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;

public interface IKernelMsgService {
    void deleteMsg(com.tencent.qqnt.kernelpublic.nativeinterface.Contact contact, ArrayList<Long> msgIdList, IOperateCallback callback);

    void fetchLongMsg(com.tencent.qqnt.kernelpublic.nativeinterface.Contact contact, long msgId);

    void fetchLongMsgWithCb(com.tencent.qqnt.kernelpublic.nativeinterface.Contact contact, long msgId, IOperateCallback back);

    void getRecallMsgsByMsgId(com.tencent.qqnt.kernelpublic.nativeinterface.Contact contact, ArrayList<Long> msgIdList, IMsgOperateCallback callback);

    void recallMsg(com.tencent.qqnt.kernelpublic.nativeinterface.Contact contact, ArrayList<Long> msgIdList, IOperateCallback callback);

    void addLocalRecordMsg(com.tencent.qqnt.kernelpublic.nativeinterface.Contact contact, long msgId, MsgElement elem, HashMap<Integer, MsgAttributeInfo> hashMap, boolean z, IOperateCallback callback);

    long getMsgUniqueId(long time);

    void addSendMsg(long msgId, com.tencent.qqnt.kernelpublic.nativeinterface.Contact contact, ArrayList<MsgElement> msgList, HashMap<Integer, MsgAttributeInfo> hashMap);

    void getMsgs(@NotNull com.tencent.qqnt.kernelpublic.nativeinterface.Contact contact, long startMsgId, int cnt, boolean queryOrder, @NotNull IMsgOperateCallback iMsgOperateCallback);

    void getMsgsIncludeSelf(com.tencent.qqnt.kernelpublic.nativeinterface.Contact contact, long startMsgId, int count, boolean queryOrder, IMsgOperateCallback iMsgOperateCallback);

    void translatePtt2Text(long j2, com.tencent.qqnt.kernelpublic.nativeinterface.Contact contact, MsgElement msgElement, IOperateCallback iOperateCallback);

    void getMultiMsg(com.tencent.qqnt.kernelpublic.nativeinterface.Contact contact, long rootMsgId, long parentMsgId, IGetMultiMsgCallback cb);

    void multiForwardMsg(ArrayList<MultiMsgInfo> arrayList, com.tencent.qqnt.kernelpublic.nativeinterface.Contact from, com.tencent.qqnt.kernelpublic.nativeinterface.Contact to, IOperateCallback cb);

    void setAllC2CAndGroupMsgRead(IOperateCallback cb);

    void clearMsgRecords(com.tencent.qqnt.kernelpublic.nativeinterface.Contact contact, IClearMsgRecordsCallback cb);

    String createUidFromTinyId(long j2, long j3);

    void switchBackGround(BackGroundInfo backGroundInfo, IOperateCallback cb);

    void switchBackGroundForMqq(byte[] bArr, IOperateCallback cb);

    void switchForeGround(IOperateCallback cb);

    void switchForeGroundForMqq(byte[] bArr, IOperateCallback cb);
}
