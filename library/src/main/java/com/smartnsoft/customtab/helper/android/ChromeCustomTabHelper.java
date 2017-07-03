// The MIT License (MIT)
//
// Copyright (c) 2017 Smart&Soft
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.

package com.smartnsoft.customtab.helper.android;

import java.util.ArrayList;
import java.util.List;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build.VERSION_CODES;
import android.support.customtabs.CustomTabsClient;
import android.support.customtabs.CustomTabsIntent;
import android.support.customtabs.CustomTabsServiceConnection;

/**
 * @author Yannick Platon
 * @since 2016.02.11
 */
@SuppressWarnings("unused")
public final class ChromeCustomTabHelper
{

  public static final String CHROME_PACKAGE_NAME = "com.android.chrome";

  public static final String ACTION_CUSTOM_TABS_CONNECTION = "android.support.customtabs.action.CustomTabsService";

  public enum FallbackType
  {
    Custom, DefaultSystemBrowser
  }

  public interface OnCustomFallbackListener
  {

    void onCustomFallback(String url);

  }

  public static final class CustomTabBuilder
  {

    public final List<MenuItemPendingIntent> menuItemPendingIntents;

    public final ActionButtonUrl actionButtonUrl;

    public final String url;

    public final int colorResource;

    public final Bitmap closeButtonIcon;

    private final boolean shouldOverrideDefaultBrowser;

    public CustomTabBuilder(List<MenuItemPendingIntent> menuItemPendingIntents, ActionButtonUrl actionButtonUrl, Bitmap closeButtonIcon, String url,
        int colorResource, boolean shouldOverrideDefaultBrowser)
    {
      this.menuItemPendingIntents = menuItemPendingIntents;
      this.actionButtonUrl = actionButtonUrl;
      this.url = url;
      this.colorResource = colorResource;
      this.closeButtonIcon = closeButtonIcon;
      this.shouldOverrideDefaultBrowser = shouldOverrideDefaultBrowser;
    }
  }

  public static final class MenuItemPendingIntent
  {

    public final String name;

    public final PendingIntent pendingIntent;

    public MenuItemPendingIntent(String name, PendingIntent pendingIntent)
    {
      this.name = name;
      this.pendingIntent = pendingIntent;
    }

  }

  public static final class ActionButtonUrl
  {

    public final Bitmap icon;

    public final String description;

    public final PendingIntent pendingIntent;

    public ActionButtonUrl(Bitmap icon, String description, PendingIntent pendingIntent)
    {
      this.icon = icon;
      this.description = description;
      this.pendingIntent = pendingIntent;
    }

  }

  private static volatile ChromeCustomTabHelper instance;

  public static ChromeCustomTabHelper getInstance()
  {
    if (instance == null)
    {
      synchronized (ChromeCustomTabHelper.class)
      {
        if (instance == null)
        {
          instance = new ChromeCustomTabHelper();
        }
      }
    }

    return instance;
  }

  private boolean isServiceConnected = false;

  public void initialize(Context context)
  {
    CustomTabsClient.bindCustomTabsService(context, ChromeCustomTabHelper.CHROME_PACKAGE_NAME, new CustomTabsServiceConnection()
    {
      @Override
      public void onCustomTabsServiceConnected(ComponentName componentName, CustomTabsClient customTabsClientBis)
      {
        isServiceConnected = true;
      }

      @Override
      public void onServiceDisconnected(ComponentName componentName)
      {
        isServiceConnected = false;
      }
    });
  }

  public void openUrl(Activity activity, FallbackType fallbackType, final CustomTabBuilder customTabBuilder)
  {
    openUrl(activity, fallbackType, customTabBuilder, null);
  }

  public void openUrl(Activity activity, FallbackType fallbackType, final CustomTabBuilder customTabBuilder, OnCustomFallbackListener onCustomFallbackListener)
  {
    if (isServiceConnected && isCustomTabAvailable(activity, customTabBuilder.shouldOverrideDefaultBrowser, customTabBuilder.url))
    {
      openCustomTab(activity, customTabBuilder);
    }
    else
    {
      switch (fallbackType)
      {
      case DefaultSystemBrowser:
        final Intent blankIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(customTabBuilder.url));
        activity.startActivity(blankIntent);
        break;
      case Custom:
        if (onCustomFallbackListener != null)
        {
          onCustomFallbackListener.onCustomFallback(customTabBuilder.url);
        }
        break;
      }
    }
  }

  @TargetApi(VERSION_CODES.DONUT)
  private void openCustomTab(Activity activity, CustomTabBuilder customTabBuilder)
  {
    final CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
    builder.setToolbarColor(customTabBuilder.colorResource);

    // Add the icon with near the url with action
    if (customTabBuilder.actionButtonUrl != null)
    {
      builder.setActionButton(customTabBuilder.actionButtonUrl.icon, customTabBuilder.actionButtonUrl.description,
          customTabBuilder.actionButtonUrl.pendingIntent);
    }

    // Adds the close icon
    builder.setCloseButtonIcon(customTabBuilder.closeButtonIcon);

    // Adds the menu item option
    if (customTabBuilder.menuItemPendingIntents != null)
    {
      for (final MenuItemPendingIntent menuItemPendingIntent : customTabBuilder.menuItemPendingIntents)
      {
        builder.addMenuItem(menuItemPendingIntent.name, menuItemPendingIntent.pendingIntent);
      }
    }

    final CustomTabsIntent customTabsIntent = builder.build();

    // builder.setStartAnimations(this, R.anim.slide_in_right, R.anim.slide_out_left);
    // builder.setExitAnimations(this, R.anim.slide_in_left, R.anim.slide_out_right);

    builder.setShowTitle(true);
    customTabsIntent.intent.setPackage(ChromeCustomTabHelper.CHROME_PACKAGE_NAME);

    customTabsIntent.launchUrl(activity, Uri.parse(customTabBuilder.url));
  }

  public boolean isCustomTabAvailable()
  {
    return isServiceConnected;
  }

  /**
   * Returns true if Custom Tabs are available AND if chrome is the default browser.
   */
  @TargetApi(VERSION_CODES.DONUT)
  public boolean isCustomTabAvailable(Context context, boolean shouldOverrideDefaultBrowser, String url)
  {
    final PackageManager pm = context.getPackageManager();
    // Get default VIEW intent handler.
    final Intent activityIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));

    final ResolveInfo defaultApp = pm.resolveActivity(activityIntent, PackageManager.MATCH_DEFAULT_ONLY);
    if (defaultApp != null && ChromeCustomTabHelper.CHROME_PACKAGE_NAME.equals(defaultApp.activityInfo.packageName))
    {
      // Chrome is default user browser, so we should try to use chrome custom tabs.
      return true;
    }
    else
    {
      // Get all apps that can handle VIEW intents.
      final List<ResolveInfo> resolvedActivityList = pm.queryIntentActivities(activityIntent, 0);

      if (resolvedActivityList != null)
      {
        for (int index = resolvedActivityList.size() - 1; index >= 0; index--)
        {
          if (context.getPackageName().equals(resolvedActivityList.get(index).activityInfo.packageName))
          {
            resolvedActivityList.remove(index);
          }
        }
      }

      if (resolvedActivityList != null && resolvedActivityList.size() == 1 && ChromeCustomTabHelper.CHROME_PACKAGE_NAME.equals(
          resolvedActivityList.get(0).activityInfo.packageName))
      {
        return true;
      }
      else
      {
        final ArrayList<ResolveInfo> packagesSupportingCustomTabs = new ArrayList<>();
        if (resolvedActivityList != null)
        {
          for (final ResolveInfo info : resolvedActivityList)
          {
            final Intent serviceIntent = new Intent();
            serviceIntent.setAction(ACTION_CUSTOM_TABS_CONNECTION);
            serviceIntent.setPackage(info.activityInfo.packageName);
            // Check if this package also resolves the Custom Tabs service.
            if (pm.resolveService(serviceIntent, 0) != null && (info.isDefault || shouldOverrideDefaultBrowser))
            {
              packagesSupportingCustomTabs.add(info);
            }
          }
        }
        return packagesSupportingCustomTabs.size() > 0;
      }
    }
  }

}