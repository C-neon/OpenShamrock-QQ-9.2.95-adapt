package com.tencent.qqnt.kernel.nativeinterface;

import java.util.ArrayList;
import java.util.HashMap;

public interface IKernelGroupListener {
    void onGetGroupBulletinListResult(long j2, String str, GroupBulletinListResult groupBulletinListResult);

    void onGroupAIOSlotInfoChange(ArrayList<GroupAIOSlotInfo> arrayList);

    void onGroupAdd(long j2);

    void onGroupAiAuthBlacklistChanged(HashMap<Long, Integer> hashMap);

    void onGroupAllInfoChange(GroupAllInfo groupAllInfo);

    void onGroupArkInviteStateResult(long j2, GroupArkInviteStateInfo groupArkInviteStateInfo);

    void onGroupBulletinChange(long j2, GroupBulletin groupBulletin);

    void onGroupBulletinRemindNotify(long j2, RemindGroupBulletinMsg remindGroupBulletinMsg);

    void onGroupBulletinRichMediaDownloadComplete(BulletinFeedsDownloadInfo bulletinFeedsDownloadInfo);

    void onGroupBulletinRichMediaProgressUpdate(BulletinFeedsDownloadInfo bulletinFeedsDownloadInfo);

    void onGroupConfMemberChange(long j2, ArrayList<String> arrayList);

    void onGroupDetailInfoChange(GroupDetailInfo groupDetailInfo);

    void onGroupDragonListChange(long j2, ArrayList<GroupDragonLadderCollectInfo> arrayList);

    void onGroupEssenceListChange(long j2);

    void onGroupExtListUpdate(GroupExtListUpdateType groupExtListUpdateType, ArrayList<GroupExtInfo> arrayList);

    void onGroupFirstBulletinNotify(FirstGroupBulletinInfo firstGroupBulletinInfo);

    void onGroupListInited(boolean z);

    void onGroupListUpdate(GroupListUpdateType groupListUpdateType, ArrayList<GroupSimpleInfo> arrayList);

    void onGroupMemberLevelInfoChange(long j2, GroupMemberLevelInfo groupMemberLevelInfo);

    void onGroupMemberNewExtInfoChange(long j2, ArrayList<GroupMemberNewExtInfo> arrayList);

    void onGroupNotifiesUnreadCountUpdated(boolean z, long j2, int i2);

    void onGroupNotifiesUnreadCountUpdatedV2(boolean z, long j2, int i2, int i3, int i4, int i5);

    void onGroupNotifiesUpdated(boolean z, ArrayList<GroupNotifyMsg> arrayList);

    void onGroupNotifiesUpdatedV2(boolean z, long j2, ArrayList<GroupNotifyMsg> arrayList, ArrayList<GroupNotifyTemplateItem> arrayList2);

    void onGroupSingleScreenNotifies(boolean z, long j2, ArrayList<GroupNotifyMsg> arrayList);

    void onGroupSingleScreenNotifiesV2(boolean z, long j2, long j3, boolean z2, int i2, ArrayList<GroupNotifyMsg> arrayList, ArrayList<GroupNotifyTemplateItem> arrayList2);

    void onGroupStatisticInfoChange(long j2, GroupStatisticInfo groupStatisticInfo);

    void onGroupStatusBarChange(long j2, QueryGroupStatusBarRsp queryGroupStatusBarRsp);

    void onGroupTopBannerDataNotify(long j2, QueryGroupTopBannersRsp queryGroupTopBannersRsp, QueryGroupTopBannersRsp queryGroupTopBannersRsp2, ArrayList<OnlineBanner> arrayList);

    void onGroupsMsgMaskResult(ArrayList<GroupMsgMaskInfo> arrayList);

    void onJoinGroupNoVerifyFlag(long j2, boolean z, boolean z2);

    void onJoinGroupNotify(JoinGroupNotifyMsg joinGroupNotifyMsg);

    void onMemberInfoChange(long j2, DataSource dataSource, HashMap<String, MemberInfo> hashMap);

    void onMemberListChange(GroupMemberListChangeInfo groupMemberListChangeInfo);

    void onQunCommonStatusBarChange(long j2, QueryQunCommonStatusBarRsp queryQunCommonStatusBarRsp);

    void onSearchMemberChange(String str, String str2, ArrayList<GroupMemberInfoListId> arrayList, HashMap<String, MemberInfo> hashMap);

    void onSetGroupMemberFlagGroceryInfoFinish(long j2, int i2, int i3);

    void onShutUpMemberListChanged(long j2, ArrayList<MemberInfo> arrayList);
}
