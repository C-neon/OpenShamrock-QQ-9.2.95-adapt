@file:OptIn(DelicateCoroutinesApi::class)

package moe.fuqiuluo.shamrock.remote.service.listener

import com.tencent.qqnt.kernel.nativeinterface.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import moe.fuqiuluo.qqinterface.servlet.GroupSvc
import moe.fuqiuluo.shamrock.helper.LogCenter
import java.util.ArrayList
import java.util.HashMap

internal object GroupEventListener: IKernelGroupListener {
    override fun onGetGroupBulletinListResult(
        j2: Long,
        str: String?,
        groupBulletinListResult: GroupBulletinListResult?
    ) {
        LogCenter.log("onGetGroupBulletinListResult($j2, $str, $groupBulletinListResult)")
    }

    override fun onGroupAIOSlotInfoChange(arrayList: ArrayList<GroupAIOSlotInfo>?) {
        LogCenter.log("onGroupAIOSlotInfoChange(${arrayList?.size ?: 0})")
    }

    override fun onGroupAdd(j2: Long) {
        LogCenter.log("onGroupAdd($j2)")
    }

    override fun onGroupAiAuthBlacklistChanged(hashMap: HashMap<Long, Int>?) {
        LogCenter.log("onGroupAiAuthBlacklistChanged(${hashMap?.size ?: 0})")
    }

    override fun onGroupAllInfoChange(groupAllInfo: GroupAllInfo?) {
        LogCenter.log("onGroupAllInfoChange($groupAllInfo)")
    }

    override fun onGroupArkInviteStateResult(
        j2: Long,
        groupArkInviteStateInfo: GroupArkInviteStateInfo?
    ) {
        LogCenter.log("onGroupArkInviteStateResult($j2, $groupArkInviteStateInfo)")
    }

    override fun onGroupBulletinChange(j2: Long, groupBulletin: GroupBulletin?) {
        LogCenter.log("onGroupBulletinChange($j2, $groupBulletin)")
    }

    override fun onGroupBulletinRemindNotify(j2: Long, remindGroupBulletinMsg: RemindGroupBulletinMsg?) {
        LogCenter.log("onGroupBulletinRemindNotify($j2, $remindGroupBulletinMsg)")
    }

    override fun onGroupBulletinRichMediaDownloadComplete(bulletinFeedsDownloadInfo: BulletinFeedsDownloadInfo?) {
        LogCenter.log("onGroupBulletinRichMediaDownloadComplete($bulletinFeedsDownloadInfo)")
    }

    override fun onGroupBulletinRichMediaProgressUpdate(bulletinFeedsDownloadInfo: BulletinFeedsDownloadInfo?) {
        LogCenter.log("onGroupBulletinRichMediaProgressUpdate($bulletinFeedsDownloadInfo)")
    }

    override fun onGroupConfMemberChange(j2: Long, arrayList: ArrayList<String>?) {
        LogCenter.log("onGroupConfMemberChange($j2, $arrayList)")
    }

    override fun onGroupDetailInfoChange(groupDetailInfo: GroupDetailInfo?) {
        LogCenter.log("onGroupDetailInfoChange($groupDetailInfo)")
    }

    override fun onGroupDragonListChange(
        j2: Long,
        arrayList: ArrayList<GroupDragonLadderCollectInfo>?
    ) {
        LogCenter.log("onGroupDragonListChange($j2, ${arrayList?.size ?: 0})")
    }

    override fun onGroupEssenceListChange(j2: Long) {
        LogCenter.log("onGroupEssenceListChange($j2)")
    }

    override fun onGroupExtListUpdate(
        groupExtListUpdateType: GroupExtListUpdateType?,
        arrayList: ArrayList<GroupExtInfo>?
    ) {
        LogCenter.log("onGroupExtListUpdate($groupExtListUpdateType, ${arrayList?.size ?: 0})")
    }

    override fun onGroupFirstBulletinNotify(firstGroupBulletinInfo: FirstGroupBulletinInfo?) {
        LogCenter.log("onGroupFirstBulletinNotify($firstGroupBulletinInfo)")
    }

    override fun onGroupListInited(z: Boolean) {
        LogCenter.log("onGroupListInited($z)")
    }

    override fun onGroupListUpdate(
        groupListUpdateType: GroupListUpdateType?,
        arrayList: ArrayList<GroupSimpleInfo>?
    ) {
        LogCenter.log("onGroupListUpdate($groupListUpdateType, $arrayList)")
    }

    override fun onGroupMemberLevelInfoChange(
        j2: Long,
        groupMemberLevelInfo: GroupMemberLevelInfo?
    ) {
        LogCenter.log("onGroupMemberLevelInfoChange($j2, $groupMemberLevelInfo)")
    }

    override fun onGroupMemberNewExtInfoChange(
        j2: Long,
        arrayList: ArrayList<GroupMemberNewExtInfo>?
    ) {
        LogCenter.log("onGroupMemberNewExtInfoChange($j2, ${arrayList?.size ?: 0})")
    }

    override fun onGroupNotifiesUnreadCountUpdated(z: Boolean, j2: Long, i2: Int) {
        LogCenter.log("onGroupNotifiesUnreadCountUpdated($z, $j2, $i2)")
    }

    override fun onGroupNotifiesUnreadCountUpdatedV2(
        z: Boolean,
        j2: Long,
        i2: Int,
        i3: Int,
        i4: Int,
        i5: Int
    ) {
        LogCenter.log("onGroupNotifiesUnreadCountUpdatedV2($z, $j2, $i2, $i3, $i4, $i5)")
    }

    override fun onGroupNotifiesUpdated(z: Boolean, arrayList: ArrayList<GroupNotifyMsg>?) {
        LogCenter.log("onGroupNotifiesUpdated($z, $arrayList)")
    }

    override fun onGroupNotifiesUpdatedV2(
        z: Boolean,
        j2: Long,
        arrayList: ArrayList<GroupNotifyMsg>?,
        arrayList2: ArrayList<GroupNotifyTemplateItem>?
    ) {
        LogCenter.log("onGroupNotifiesUpdatedV2($z, $j2, ${arrayList?.size ?: 0}, ${arrayList2?.size ?: 0})")
    }

    override fun onGroupSingleScreenNotifies(
        z: Boolean,
        j2: Long,
        arrayList: ArrayList<GroupNotifyMsg>?
    ) {
        LogCenter.log("onGroupSingleScreenNotifies($z, $j2, $arrayList)")
    }

    override fun onGroupSingleScreenNotifiesV2(
        z: Boolean,
        j2: Long,
        j3: Long,
        z2: Boolean,
        i2: Int,
        arrayList: ArrayList<GroupNotifyMsg>?,
        arrayList2: ArrayList<GroupNotifyTemplateItem>?
    ) {
        LogCenter.log("onGroupSingleScreenNotifiesV2($z, $j2, $j3, $z2, $i2, ${arrayList?.size ?: 0}, ${arrayList2?.size ?: 0})")
    }

    override fun onGroupStatisticInfoChange(j2: Long, groupStatisticInfo: GroupStatisticInfo?) {
        LogCenter.log("onGroupStatisticInfoChange($j2, $groupStatisticInfo)")
    }

    override fun onGroupStatusBarChange(j2: Long, queryGroupStatusBarRsp: QueryGroupStatusBarRsp?) {
        LogCenter.log("onGroupStatusBarChange($j2, $queryGroupStatusBarRsp)")
    }

    override fun onGroupTopBannerDataNotify(
        j2: Long,
        queryGroupTopBannersRsp: QueryGroupTopBannersRsp?,
        queryGroupTopBannersRsp2: QueryGroupTopBannersRsp?,
        arrayList: ArrayList<OnlineBanner>?
    ) {
        LogCenter.log("onGroupTopBannerDataNotify($j2, $queryGroupTopBannersRsp, $queryGroupTopBannersRsp2, ${arrayList?.size ?: 0})")
    }

    override fun onGroupsMsgMaskResult(arrayList: ArrayList<GroupMsgMaskInfo>?) {
        LogCenter.log("onGroupsMsgMaskResult($arrayList)")
    }

    override fun onJoinGroupNoVerifyFlag(j2: Long, z: Boolean, z2: Boolean) {
        LogCenter.log("onJoinGroupNoVerifyFlag($j2, $z, $z2)")
    }

    override fun onJoinGroupNotify(joinGroupNotifyMsg: JoinGroupNotifyMsg?) {
        LogCenter.log("onJoinGroupNotify($joinGroupNotifyMsg)")
    }

    override fun onMemberInfoChange(
        groupCode: Long,
        dataSource: DataSource,
        hashMap: HashMap<String, MemberInfo>
    ) {
        /*GlobalScope.launch {
            hashMap.values.forEach { memberInfo ->
                GroupSvc.getTroopMemberInfoByUid(groupCode, memberInfo.uid).onSuccess {
                    LogCenter.log("onMemberInfoChange($groupCode, $dataSource, $it, $memberInfo)")
                }.onFailure {
                    LogCenter.log("onMemberInfoChange($groupCode, $dataSource, $it, $memberInfo)")
                }
            }
        }*/
    }

    override fun onMemberListChange(groupMemberListChangeInfo: GroupMemberListChangeInfo?) {
        LogCenter.log("onMemberListChange($groupMemberListChangeInfo)")
    }

    override fun onQunCommonStatusBarChange(
        j2: Long,
        queryQunCommonStatusBarRsp: QueryQunCommonStatusBarRsp?
    ) {
        LogCenter.log("onQunCommonStatusBarChange($j2, $queryQunCommonStatusBarRsp)")
    }

    override fun onSearchMemberChange(
        str: String?,
        str2: String?,
        arrayList: ArrayList<GroupMemberInfoListId>?,
        hashMap: HashMap<String, MemberInfo>?
    ) {
        LogCenter.log("onSearchMemberChange($str, $str2, $arrayList, $hashMap)")
    }

    override fun onSetGroupMemberFlagGroceryInfoFinish(j2: Long, i2: Int, i3: Int) {
        LogCenter.log("onSetGroupMemberFlagGroceryInfoFinish($j2, $i2, $i3)")
    }

    override fun onShutUpMemberListChanged(j2: Long, arrayList: ArrayList<MemberInfo>?) {
        LogCenter.log("onShutUpMemberListChanged($j2, $arrayList)")
    }
}
