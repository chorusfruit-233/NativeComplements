package dev.local.sunwindowdemo.features.callblocking;

import org.junit.Test;
import static org.junit.Assert.*;

public class CallFilteringCopyTest {
    public static class Result {
        boolean shouldAllowCall=true,shouldReject,shouldSilence,shouldScreenViaAudio,shouldAddToCallLog=true,shouldShowNotification=true;
        int mCallBlockReason=7;
        CharSequence mCallScreeningAppName="native";
        String mCallScreeningComponentName="native/component";
        Object mCallScreeningResponse=new Object();
        boolean mIsResponseFromSystemDialer=true,contactExists=true,shouldSuppressCallDueToDndStatus=true;
    }
    public static class LegacyBuilder {
        boolean mShouldAllowCall,mShouldReject,mShouldSilence,mShouldScreenViaAudio,mShouldAddToCallLog,mShouldShowNotification;
        int mCallBlockReason;
        CharSequence mCallScreeningAppName;
        String mCallScreeningComponentName;
        Object mCallScreeningResponse;
        boolean mIsResponseFromSystemDialer,mContactExists,mDndSuppressed;
    }
    public static class ModernBuilder {
        Result original;
        public static ModernBuilder from(Result result){ModernBuilder builder=new ModernBuilder();builder.original=result;return builder;}
    }
    public static class UnknownResult extends Result { boolean unknownNativeFact=true; }
    public static class InvalidModernBuilder { public ModernBuilder from(Result result){return null;} }

    @Test public void api35CopyPreservesContactsDndReasonAndNativeScreeningIdentity()throws Exception{
        Result original=new Result();CallFilteringCopy copy=new CallFilteringCopy(Result.class,LegacyBuilder.class);
        LegacyBuilder builder=(LegacyBuilder)copy.copy(original);
        assertTrue(builder.mShouldAllowCall);assertTrue(builder.mShouldAddToCallLog);assertTrue(builder.mShouldShowNotification);
        assertTrue(builder.mContactExists);assertTrue(builder.mDndSuppressed);assertTrue(builder.mIsResponseFromSystemDialer);
        assertEquals(7,builder.mCallBlockReason);assertSame(original.mCallScreeningResponse,builder.mCallScreeningResponse);
        assertEquals("native/component",builder.mCallScreeningComponentName);
        builder.mShouldAllowCall=false;builder.mShouldReject=true;
        assertTrue(original.shouldAllowCall);assertFalse(original.shouldReject);assertTrue(original.contactExists);
    }
    @Test public void modernCopyKeepsUsingTheNativesOwnFactory()throws Exception{
        Result original=new Result();ModernBuilder builder=(ModernBuilder)new CallFilteringCopy(Result.class,ModernBuilder.class).copy(original);
        assertSame(original,builder.original);
        assertThrows(NoSuchMethodException.class,()->new CallFilteringCopy(Result.class,InvalidModernBuilder.class));
    }
    @Test public void unknownOrMissingNativeFactsRejectTheLegacyCopyBeforeHooking(){
        assertThrows(NoSuchFieldException.class,()->new CallFilteringCopy(UnknownResult.class,LegacyBuilder.class));
    }
}
