package com.isoftstone.circleimageview_harmonyos.slice;

import com.isoftstone.circleimageview.CircleImageView;
import com.isoftstone.circleimageview_harmonyos.ResourceTable;
import ohos.aafwk.ability.AbilitySlice;
import ohos.aafwk.content.Intent;

public class MainAbilitySlice extends AbilitySlice {
    @Override
    public void onStart(Intent intent) {
        super.onStart(intent);
        super.setUIContent(ResourceTable.Layout_ability_main);
        CircleImageView imageView = (CircleImageView) findComponentById(ResourceTable.Id_image);
        imageView.setImageAndDecodeBounds(ResourceTable.Media_hugh);

    }

    @Override
    public void onActive() {
        super.onActive();
    }

    @Override
    public void onForeground(Intent intent) {
        super.onForeground(intent);
    }
}
