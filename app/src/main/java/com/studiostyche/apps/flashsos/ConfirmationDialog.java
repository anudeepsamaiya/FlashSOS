package com.studiostyche.apps.flashsos;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;

/**
 * Created by AnudeepSamaiya on 02-05-16.
 */
public class ConfirmationDialog extends DialogFragment {
    private static final String REQUEST_CAMERA_PERMISSION  = "camera_permission";

    public static ConfirmationDialog newInstance(int requestPermission) {
        ConfirmationDialog dialog = new ConfirmationDialog();
        Bundle args = new Bundle();
        args.putInt(REQUEST_CAMERA_PERMISSION, requestPermission);
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Fragment parent = getParentFragment();
        return new AlertDialog.Builder(getActivity())
                .setMessage(R.string.request_permission)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ActivityCompat.requestPermissions(getActivity(),
                                new String[]{Manifest.permission.CAMERA},
                                getArguments().getInt(REQUEST_CAMERA_PERMISSION));
                    }
                })
                .setNegativeButton(android.R.string.cancel,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Activity activity = parent.getActivity();
                                if (activity != null) {
                                    activity.finish();
                                }
                            }
                        })
                .create();
    }
}
