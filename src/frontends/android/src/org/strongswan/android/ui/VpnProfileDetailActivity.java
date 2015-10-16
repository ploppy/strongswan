/*
 * Copyright (C) 2012-2014 Tobias Brunner
 * Copyright (C) 2012 Giuliano Grassi
 * Copyright (C) 2012 Ralf Sager
 * Hochschule fuer Technik Rapperswil
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation; either version 2 of the License, or (at your
 * option) any later version.  See <http://www.fsf.org/copyleft/gpl.txt>.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * for more details.
 */

package org.strongswan.android.ui;

import java.security.cert.X509Certificate;

import org.strongswan.android.R;
import org.strongswan.android.data.VpnProfile;
import org.strongswan.android.data.VpnProfileDataSource;
import org.strongswan.android.data.VpnType;
import org.strongswan.android.data.VpnType.VpnTypeFeature;
import org.strongswan.android.databinding.ProfileDetailViewBinding;
import org.strongswan.android.logic.TrustedCertificateManager;
import org.strongswan.android.security.TrustedCertificateEntry;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.os.AsyncTask;
import android.os.Bundle;
import android.security.KeyChain;
import android.security.KeyChainAliasCallback;
import android.security.KeyChainException;
import android.text.Html;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.TextView;

import com.google.common.collect.EnumHashBiMap;

public class VpnProfileDetailActivity extends Activity
{
	private static final int SELECT_TRUSTED_CERTIFICATE = 0;
	private static final int MTU_MIN = 1280;
	private static final int MTU_MAX = 1500;

	private VpnProfileDataSource mDataSource;
	private ProfileDetailViewBinding mBinding;
	/** id of profile being edited, or null if creating a new profile */
	private Long mId;
	private TrustedCertificateEntry mCertEntry;
	private String mUserCertLoading;
	private TrustedCertificateEntry mUserCertEntry;
	private VpnProfile mProfile;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		/* the title is set when we load the profile, if any */
		getActionBar().setDisplayHomeAsUpEnabled(true);

		mDataSource = new VpnProfileDataSource(this);
		mDataSource.open();

		mBinding = DataBindingUtil.setContentView(this, R.layout.profile_detail_view);

		mBinding.vpnType.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				mProfile.setVpnType(VpnType.values()[position]);
				updateCredentialView();
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {	/* should not happen */
				mProfile.setVpnType(VpnType.IKEV2_EAP);
				updateCredentialView();
			}
		});

		((TextView)mBinding.tncNotice.getRoot().findViewById(android.R.id.text1)).setText(R.string.tnc_notice_title);
		((TextView)mBinding.tncNotice.getRoot().findViewById(android.R.id.text2)).setText(R.string.tnc_notice_subtitle);
		mBinding.tncNotice.getRoot().setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				new TncNoticeDialog().show(VpnProfileDetailActivity.this.getFragmentManager(), "TncNotice");
			}
		});

		mBinding.selectUserCertificate.getRoot().setOnClickListener(new SelectUserCertOnClickListener());

		mBinding.caAuto.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				updateCertificateSelector();
			}
		});

		mBinding.selectAnchorCertificate.getRoot().setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(VpnProfileDetailActivity.this, TrustedCertificatesActivity.class);
				intent.setAction(TrustedCertificatesActivity.SELECT_CERTIFICATE);
				startActivityForResult(intent, SELECT_TRUSTED_CERTIFICATE);
			}
		});

		mBinding.showAdvanced.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
			{
				updateAdvancedSettings();
			}
		});

		mId = savedInstanceState == null ? null : savedInstanceState.getLong(VpnProfileDataSource.KEY_ID);
		if (mId == null)
		{
			Bundle extras = getIntent().getExtras();
			mId = extras == null ? null : extras.getLong(VpnProfileDataSource.KEY_ID);
		}

		loadProfileData(savedInstanceState);

		updateCredentialView();
		updateCertificateSelector();
		updateAdvancedSettings();
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		mDataSource.close();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState)
	{
		super.onSaveInstanceState(outState);
		if (mId != null)
		{
			outState.putLong(VpnProfileDataSource.KEY_ID, mId);
		}
		if (mUserCertEntry != null)
		{
			outState.putString(VpnProfileDataSource.KEY_USER_CERTIFICATE, mUserCertEntry.getAlias());
		}
		if (mCertEntry != null)
		{
			outState.putString(VpnProfileDataSource.KEY_CERTIFICATE, mCertEntry.getAlias());
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.profile_edit, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case android.R.id.home:
			case R.id.menu_cancel:
				finish();
				return true;
			case R.id.menu_accept:
				saveProfile();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		switch (requestCode)
		{
			case SELECT_TRUSTED_CERTIFICATE:
				if (resultCode == RESULT_OK)
				{
					String alias = data.getStringExtra(VpnProfileDataSource.KEY_CERTIFICATE);
					X509Certificate certificate = TrustedCertificateManager.getInstance().getCACertificateFromAlias(alias);
					mCertEntry = certificate == null ? null : new TrustedCertificateEntry(alias, certificate);
					updateCertificateSelector();
				}
				break;
			default:
				super.onActivityResult(requestCode, resultCode, data);
		}
	}

	/**
	 * Update the UI to enter credentials depending on the type of VPN currently selected
	 */
	private void updateCredentialView()
	{
		mBinding.tncNotice.getRoot().setVisibility(mProfile.getVpnType().has(VpnTypeFeature.BYOD) ? View.VISIBLE : View.GONE);

		if (mProfile.getVpnType().has(VpnTypeFeature.CERTIFICATE))
		{
			if (mUserCertLoading != null)
			{
				((TextView)mBinding.selectUserCertificate.getRoot().findViewById(android.R.id.text1)).setText(mUserCertLoading);
				((TextView)mBinding.selectUserCertificate.getRoot().findViewById(android.R.id.text2)).setText(R.string.loading);
			}
			else if (mUserCertEntry != null)
			{	/* clear any errors and set the new data */
				((TextView)mBinding.selectUserCertificate.getRoot().findViewById(android.R.id.text1)).setError(null);
				((TextView)mBinding.selectUserCertificate.getRoot().findViewById(android.R.id.text1)).setText(mUserCertEntry.getAlias());
				((TextView)mBinding.selectUserCertificate.getRoot().findViewById(android.R.id.text2)).setText(mUserCertEntry.getCertificate().getSubjectDN().toString());
			}
			else
			{
				((TextView)mBinding.selectUserCertificate.getRoot().findViewById(android.R.id.text1)).setText(R.string.profile_user_select_certificate_label);
				((TextView)mBinding.selectUserCertificate.getRoot().findViewById(android.R.id.text2)).setText(R.string.profile_user_select_certificate);
			}
		}
	}

	/**
	 * Show an alert in case the previously selected certificate is not found anymore
	 * or the user did not select a certificate in the spinner.
	 */
	private void showCertificateAlert()
	{
		AlertDialog.Builder adb = new AlertDialog.Builder(VpnProfileDetailActivity.this);
		adb.setTitle(R.string.alert_text_nocertfound_title);
		adb.setMessage(R.string.alert_text_nocertfound);
		adb.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int id)
			{
				dialog.cancel();
			}
		});
		adb.show();
	}

	/**
	 * Update the CA certificate selection UI depending on whether the
	 * certificate should be automatically selected or not.
	 */
	private void updateCertificateSelector()
	{
		if (!mBinding.caAuto.isChecked())
		{
			mBinding.selectAnchorCertificate.getRoot().setEnabled(true);
			mBinding.selectAnchorCertificate.getRoot().setVisibility(View.VISIBLE);

			if (mCertEntry != null)
			{
				((TextView)mBinding.selectAnchorCertificate.getRoot().findViewById(android.R.id.text1)).setText(mCertEntry.getSubjectPrimary());
				((TextView)mBinding.selectAnchorCertificate.getRoot().findViewById(android.R.id.text2)).setText(mCertEntry.getSubjectSecondary());
			}
			else
			{
				((TextView)mBinding.selectAnchorCertificate.getRoot().findViewById(android.R.id.text1)).setText(R.string.profile_ca_select_certificate_label);
				((TextView)mBinding.selectAnchorCertificate.getRoot().findViewById(android.R.id.text2)).setText(R.string.profile_ca_select_certificate);
			}
		}
		else
		{
			mBinding.selectAnchorCertificate.getRoot().setEnabled(false);
			mBinding.selectAnchorCertificate.getRoot().setVisibility(View.GONE);
		}
	}

	/**
	 * Update the advanced settings UI depending on whether any advanced
	 * settings have already been made.
	 */
	private void updateAdvancedSettings()
	{
		boolean show = mBinding.showAdvanced.isChecked();
		if (!show && mProfile != null)
		{
			Integer st = mProfile.getSplitTunneling();
			show = mProfile.getMTU() != null || mProfile.getPort() != null || (st != null && st != 0);
		}
		mBinding.showAdvanced.setVisibility(!show ? View.VISIBLE : View.GONE);
		mBinding.advancedSettings.setVisibility(show ? View.VISIBLE : View.GONE);
	}

	/**
	 * Save or update the profile depending on whether we actually have a
	 * profile object or not (this was created in updateProfileData)
	 */
	private void saveProfile()
	{
		if (verifyInput())
		{
			updateProfileData();
			if (mId != null)
			{
				mDataSource.updateVpnProfile(mProfile);
			}
			else
			{
				mProfile = mDataSource.insertProfile(mProfile);
				mId = mProfile.getId();
			}
			setResult(RESULT_OK, new Intent().putExtra(VpnProfileDataSource.KEY_ID, mProfile.getId()));
			finish();
		}
	}

	/**
	 * Verify the user input and display error messages.
	 * @return true if the input is valid
	 */
	private boolean verifyInput()
	{
		boolean valid = true;
		if (mBinding.gateway.getText().toString().trim().isEmpty())
		{
			mBinding.gateway.setError(getString(R.string.alert_text_no_input_gateway));
			valid = false;
		}
		if (mProfile.getVpnType().has(VpnTypeFeature.USER_PASS))
		{
			if (mBinding.username.getText().toString().trim().isEmpty())
			{
				mBinding.username.setError(getString(R.string.alert_text_no_input_username));
				valid = false;
			}
		}
		if (mProfile.getVpnType().has(VpnTypeFeature.CERTIFICATE) && mUserCertEntry == null)
		{	/* let's show an error icon */
			((TextView)mBinding.selectUserCertificate.getRoot().findViewById(android.R.id.text1)).setError("");
			valid = false;
		}
		if (!mBinding.caAuto.isChecked() && mCertEntry == null)
		{
			showCertificateAlert();
			valid = false;
		}
		Integer mtu = getInteger(mBinding.mtu);
		if (mtu != null && (mtu < MTU_MIN || mtu > MTU_MAX))
		{
			mBinding.mtu.setError(String.format(getString(R.string.alert_text_out_of_range), MTU_MIN, MTU_MAX));
			valid = false;
		}
		Integer port = getInteger(mBinding.port);
		if (port != null && (port < 1 || port > 65535))
		{
			mBinding.port.setError(String.format(getString(R.string.alert_text_out_of_range), 1, 65535));
			valid = false;
		}
		return valid;
	}

	/**
	 * Update the profile object with the data entered by the user
	 */
	private void updateProfileData()
	{
		/* the name is optional, we default to the gateway if none is given */
		String name = mBinding.name.getText().toString().trim();
		String gateway = mBinding.gateway.getText().toString().trim();
		mProfile.setName(name.isEmpty() ? gateway : name);
		mProfile.setGateway(gateway);
		if (mProfile.getVpnType().has(VpnTypeFeature.USER_PASS))
		{
			mProfile.setUsername(mBinding.username.getText().toString().trim());
			String password = mBinding.password.getText().toString().trim();
			password = password.isEmpty() ? null : password;
			mProfile.setPassword(password);
		}
		if (mProfile.getVpnType().has(VpnTypeFeature.CERTIFICATE))
		{
			mProfile.setUserCertificateAlias(mUserCertEntry.getAlias());
		}
		String certAlias = mBinding.caAuto.isChecked() ? null : mCertEntry.getAlias();
		mProfile.setCertificateAlias(certAlias);
		mProfile.setMTU(getInteger(mBinding.mtu));
		mProfile.setPort(getInteger(mBinding.port));
		int st = 0;
		st |= mBinding.splitTunnelingV4.isChecked() ? VpnProfile.SPLIT_TUNNELING_BLOCK_IPV4 : 0;
		st |= mBinding.splitTunnelingV6.isChecked() ? VpnProfile.SPLIT_TUNNELING_BLOCK_IPV6 : 0;
		mProfile.setSplitTunneling(st == 0 ? null : st);
		mProfile.setDpd(dpdMap.inverse().get(mBinding.dpd.getSelectedItemPosition()));
	}

	/**
	 * Load an existing profile if we got an ID
	 *
	 * @param savedInstanceState previously saved state
	 */
	private void loadProfileData(Bundle savedInstanceState)
	{
		String useralias = null, alias = null;

		getActionBar().setTitle(R.string.add_profile);
		if (mId != null && mId != 0)
		{
			mProfile = mDataSource.getVpnProfile(mId);
			if (mProfile != null)
			{
				mBinding.splitTunnelingV4.setChecked(mProfile.getSplitTunneling() != null ? (mProfile.getSplitTunneling() & VpnProfile.SPLIT_TUNNELING_BLOCK_IPV4) != 0 : false);
				mBinding.splitTunnelingV6.setChecked(mProfile.getSplitTunneling() != null ? (mProfile.getSplitTunneling() & VpnProfile.SPLIT_TUNNELING_BLOCK_IPV6) != 0 : false);
				useralias = mProfile.getUserCertificateAlias();
				alias = mProfile.getCertificateAlias();
				getActionBar().setTitle(mProfile.getName());
			}
			else
			{
				Log.e(VpnProfileDetailActivity.class.getSimpleName(),
					  "VPN profile with id " + mId + " not found");
				finish();
			}
		} else {
			mProfile = new VpnProfile();
			mProfile.setVpnType(VpnType.IKEV2_EAP);
			mProfile.setDpd(VpnProfile.DEFAULT_DPD_LEVEL);
		}
		mBinding.setProfile(mProfile);

		mBinding.vpnType.setSelection(mProfile.getVpnType().ordinal());
		mBinding.dpd.setSelection(dpdMap.get(mProfile.getDpd()));

		/* check if the user selected a user certificate previously */
		useralias = savedInstanceState == null ? useralias: savedInstanceState.getString(VpnProfileDataSource.KEY_USER_CERTIFICATE);
		if (useralias != null)
		{
			UserCertificateLoader loader = new UserCertificateLoader(this, useralias);
			mUserCertLoading = useralias;
			loader.execute();
		}

		/* check if the user selected a CA certificate previously */
		alias = savedInstanceState == null ? alias : savedInstanceState.getString(VpnProfileDataSource.KEY_CERTIFICATE);
		mBinding.caAuto.setChecked(alias == null);
		if (alias != null)
		{
			X509Certificate certificate = TrustedCertificateManager.getInstance().getCACertificateFromAlias(alias);
			if (certificate != null)
			{
				mCertEntry = new TrustedCertificateEntry(alias, certificate);
			}
			else
			{	/* previously selected certificate is not here anymore */
				showCertificateAlert();
				mCertEntry = null;
			}
		}
	}

	/**
	 * Get the integer value in the given text box or null if empty
	 *
	 * @param view text box (numeric entry assumed)
	 */
	private Integer getInteger(EditText view)
	{
		String value = view.getText().toString().trim();
		return value.isEmpty() ? null : Integer.valueOf(value);
	}

	private class SelectUserCertOnClickListener implements OnClickListener, KeyChainAliasCallback
	{
		@Override
		public void onClick(View v)
		{
			String useralias = mUserCertEntry != null ? mUserCertEntry.getAlias() : null;
			KeyChain.choosePrivateKeyAlias(VpnProfileDetailActivity.this, this, new String[] { "RSA" }, null, null, -1, useralias);
		}

		@Override
		public void alias(final String alias)
		{
			if (alias != null)
			{	/* otherwise the dialog was canceled, the request denied */
				try
				{
					final X509Certificate[] chain = KeyChain.getCertificateChain(VpnProfileDetailActivity.this, alias);
					/* alias() is not called from our main thread */
					runOnUiThread(new Runnable() {
						@Override
						public void run()
						{
							if (chain != null && chain.length > 0)
							{
								mUserCertEntry = new TrustedCertificateEntry(alias, chain[0]);
							}
							updateCredentialView();
						}
					});
				}
				catch (KeyChainException e)
				{
					e.printStackTrace();
				}
				catch (InterruptedException e)
				{
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * Load the selected user certificate asynchronously.  This cannot be done
	 * from the main thread as getCertificateChain() calls back to our main
	 * thread to bind to the KeyChain service resulting in a deadlock.
	 */
	private class UserCertificateLoader extends AsyncTask<Void, Void, X509Certificate>
	{
		private final Context mContext;
		private final String mAlias;

		public UserCertificateLoader(Context context, String alias)
		{
			mContext = context;
			mAlias = alias;
		}

		@Override
		protected X509Certificate doInBackground(Void... params)
		{
			X509Certificate[] chain = null;
			try
			{
				chain = KeyChain.getCertificateChain(mContext, mAlias);
			}
			catch (KeyChainException e)
			{
				e.printStackTrace();
			}
			catch (InterruptedException e)
			{
				e.printStackTrace();
			}
			if (chain != null && chain.length > 0)
			{
				return chain[0];
			}
			return null;
		}

		@Override
		protected void onPostExecute(X509Certificate result)
		{
			if (result != null)
			{
				mUserCertEntry = new TrustedCertificateEntry(mAlias, result);
			}
			else
			{	/* previously selected certificate is not here anymore */
				((TextView)mBinding.selectUserCertificate.getRoot().findViewById(android.R.id.text1)).setError("");
				mUserCertEntry = null;
			}
			mUserCertLoading = null;
			updateCredentialView();
		}
	}

	/**
	 * Dialog with notification message if EAP-TNC is used.
	 */
	public static class TncNoticeDialog extends DialogFragment
	{
		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState)
		{
			return new AlertDialog.Builder(getActivity())
				.setTitle(R.string.tnc_notice_title)
				.setMessage(Html.fromHtml(getString(R.string.tnc_notice_details)))
				.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int id)
					{
						dialog.dismiss();
					}
				}).create();
		}
	}

	private static final EnumHashBiMap<VpnProfile.DpdLevel, Integer> dpdMap;
	static {
		dpdMap = EnumHashBiMap.create(VpnProfile.DpdLevel.class);
		dpdMap.put(VpnProfile.DpdLevel.OFF, 0);
		dpdMap.put(VpnProfile.DpdLevel.LOW, 1);
		dpdMap.put(VpnProfile.DpdLevel.MEDIUM, 2);
		dpdMap.put(VpnProfile.DpdLevel.HIGH, 3);
	}
}
