package com.tencent.qqnt.kernel.nativeinterface;

import com.tencent.qqnt.kernelpublic.nativeinterface.Contact;
import java.util.ArrayList;
import java.util.HashMap;

public interface IKernelMsgListener {
    void onAddSendMsg(MsgRecord msgRecord);

    void onBusinessNotification(ArrayList<BusinessNotification> arrayList);

    void onContactUnreadCntUpdate(HashMap<Integer, HashMap<String, UnreadCntInfo>> map);

    void onCustomWithdrawConfigUpdate(CustomWithdrawConfig customWithdrawConfig);

    void onDraftUpdate(Contact contact, ArrayList<MsgElement> arrayList, long j16);

    void onEmojiDownloadComplete(EmojiNotifyInfo emojiNotifyInfo);

    void onEmojiResourceUpdate(EmojiResourceInfo emojiResourceInfo);

    void onFileMsgCome(ArrayList<MsgRecord> arrayList);

    void onGrabPasswordRedBag(int i16, String str, int i17, RecvdOrder recvdOrder, MsgRecord msgRecord);

    void onGroupFileInfoAdd(GroupItem groupItem);

    void onGroupFileInfoUpdate(GroupFileListResult groupFileListResult);

    void onGroupTransferInfoAdd(GroupItem groupItem);

    void onGroupTransferInfoUpdate(GroupFileListResult groupFileListResult);

    void onHitCsRelatedEmojiResult(DownloadRelateEmojiResultInfo downloadRelateEmojiResultInfo);

    void onHitEmojiKeywordResult(HitRelatedEmojiWordsResult hitRelatedEmojiWordsResult);

    void onHitRelatedEmojiResult(RelatedWordEmojiInfo relatedWordEmojiInfo);

    void onImportOldDbProgressUpdate(ImportOldDbMsgNotifyInfo importOldDbMsgNotifyInfo);

    void onInputStatusPush(InputStatusInfo inputStatusInfo);

    void onKickedOffLine(KickedInfo kickedInfo);

    void onLineDev(ArrayList<DevInfo> arrayList);

    void onLogLevelChanged(long j16);

    void onMiscDataChanged(ArrayList<MiscData> arrayList);

    void onMsgBoxChanged(ArrayList<ContactMsgBoxInfo> arrayList);

    void onMsgDelete(Contact contact, ArrayList<Long> arrayList);

    void onMsgInfoListAdd(ArrayList<MsgRecord> arrayList);

    void onMsgInfoListUpdate(ArrayList<MsgRecord> arrayList);

    void onMsgQRCodeStatusChanged(int i16);

    void onMsgRecall(int i16, String str, long j16);

    void onMsgSecurityNotify(MsgRecord msgRecord);

    void onMsgSettingUpdate(MsgSetting msgSetting);

    void onMsgWithRichLinkInfoUpdate(ArrayList<MsgRecord> arrayList);

    void onNtFirstViewMsgSyncEnd();

    void onNtMsgSyncContactUnread();

    void onNtMsgSyncEnd();

    void onNtMsgSyncStart();

    void onRecvMsg(ArrayList<MsgRecord> arrayList);

    void onRecvMsgSvrRspTransInfo(long j16, Contact contact, int i16, int i17, String str, byte[] bArr);

    void onRecvOnlineFileMsg(ArrayList<MsgRecord> arrayList);

    void onRecvSysMsg(ArrayList<Byte> arrayList);

    void onRedTouchChanged();

    void onRichMediaDownloadComplete(FileTransNotifyInfo fileTransNotifyInfo);

    void onRichMediaProgerssUpdate(FileTransNotifyInfo fileTransNotifyInfo);

    void onRichMediaUploadComplete(FileTransNotifyInfo fileTransNotifyInfo);

    void onSearchGroupFileInfoUpdate(SearchGroupFileResult searchGroupFileResult);

    void onSendMsgError(long j16, Contact contact, int i16, String str);

    void onSysMsgNotification(int i16, long j16, long j17, boolean z16, ArrayList<Byte> arrayList);

    void onTempChatInfoUpdate(TempChatInfo tempChatInfo);

    void onUserChannelTabStatusChanged(boolean z16);

    void onUserOnlineStatusChanged(boolean z16);

    void onUserSecQualityChanged(QueryUserSecQualityRsp queryUserSecQualityRsp);

    void onUserTabStatusChanged(ArrayList<TabStatusInfo> arrayList);

    void onlineStatusBigIconDownloadPush(int i16, long j16, String str);

    void onlineStatusSmallIconDownloadPush(int i16, long j16, String str);
}
