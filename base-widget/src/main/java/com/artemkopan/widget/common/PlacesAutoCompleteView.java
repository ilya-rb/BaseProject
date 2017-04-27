package com.artemkopan.widget.common;

import android.content.Context;
import android.content.res.TypedArray;
import android.location.Address;
import android.location.Geocoder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.AppCompatAutoCompleteTextView;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.TextView;

import com.artemkopan.widget.R;
import com.artemkopan.widget.vector.VectorCompatViewHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.ObservableSource;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;

/**
 * Created by Artem Kopan for jabrool
 * 26.01.17
 */

public class PlacesAutoCompleteView extends AppCompatAutoCompleteTextView {

    private static final String TAG = "PlacesAutoCompleteView";
    private static final int DEBOUNCE = 800;
    private static final int MIN_START_SEARCH = 2;
    private static final int MAX_RESULT = 10;
    private Geocoder geocoder;
    private Predicate<String> filter;
    private Consumer<List<Address>> consumer;
    private Function<CharSequence, String> convert;
    private PublishSubject<CharSequence> textChangeSubject;
    private Function<String, ObservableSource<List<Address>>> findItem;
    private @Nullable ArrayAdapter<Address> addressAdapter;
    private OnLocationListener loadingListener;
    private String oldValue;
    private int debounce = DEBOUNCE;
    private int minStartSearch = MIN_START_SEARCH;
    private int maxResult = MAX_RESULT;
    private boolean initAdapter = true;
    private boolean blocked;

    public PlacesAutoCompleteView(Context context) {
        super(context);
        init(null);
    }

    public PlacesAutoCompleteView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public PlacesAutoCompleteView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    private void init(AttributeSet attrs) {
        geocoder = new Geocoder(getContext());
        textChangeSubject = PublishSubject.create();

        if (attrs != null) {
            TypedArray ta = getContext().obtainStyledAttributes(attrs, R.styleable.PlacesAutoCompleteView);

            try {
                debounce = ta.getInteger(R.styleable.PlacesAutoCompleteView_gac_debounce, DEBOUNCE);
                minStartSearch = ta.getInteger(R.styleable.PlacesAutoCompleteView_gac_min_start_search, MIN_START_SEARCH);
                maxResult = ta.getInteger(R.styleable.PlacesAutoCompleteView_gac_max_result, MAX_RESULT);
                initAdapter = ta.getBoolean(R.styleable.PlacesAutoCompleteView_gac_init_adapter, true);
            } finally {
                ta.recycle();
            }
        }

        VectorCompatViewHelper.loadFromAttributes(this, attrs);

        if (initAdapter) {
            addressAdapter = new AddressAdapter(getContext(), 0, new ArrayList<Address>());
            super.setAdapter(addressAdapter);
        }
    }

    public void setText(CharSequence text, boolean blocked) {
        if (blocked) replaceText(text);
        else setText(text);
    }

    @Override
    protected void replaceText(CharSequence text) {
        blocked = true;
        super.replaceText(text);
        oldValue = text.toString();
        blocked = false;
    }

    @Override
    protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter);
        if (text.length() >= minStartSearch && textChangeSubject != null && !blocked) {
            Log.i(TAG, "onNext " + text + " " + oldValue);
            textChangeSubject.onNext(trim(text));
        } else if (!blocked && addressAdapter != null) {
            Log.i(TAG, "clear");
            addressAdapter.clear();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        textChangeSubject
                .subscribeOn(Schedulers.io())
                .map(getRxConvert())
                .filter(getRxFilter())
                .debounce(debounce, TimeUnit.MILLISECONDS, Schedulers.io())
                .switchMap(getRxFinder())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(getRxConsumer());
    }

    @Override
    protected void onDetachedFromWindow() {
        textChangeSubject.onComplete();
        super.onDetachedFromWindow();
    }

    public void setAdapter(ArrayAdapter<Address> adapter) {
        super.setAdapter(adapter);
        addressAdapter = adapter;
    }

    //==============================================================================================
    // Rx Methods
    //==============================================================================================
    //region methods

    public void setLoadingListener(OnLocationListener loadingListener) {
        this.loadingListener = loadingListener;
    }

    private Function<CharSequence, String> getRxConvert() {
        if (convert == null) {
            convert = new Function<CharSequence, String>() {
                @Override
                public String apply(CharSequence charSequence) throws Exception {
                    return charSequence.toString();
                }
            };
        }
        return convert;
    }

    private Function<String, ObservableSource<List<Address>>> getRxFinder() {
        if (findItem == null) {
            findItem = new Function<String, ObservableSource<List<Address>>>() {
                @Override
                public ObservableSource<List<Address>> apply(final String s) throws Exception {
                    return Observable
                            .create(new ObservableOnSubscribe<List<Address>>() {
                                @Override
                                public void subscribe(ObservableEmitter<List<Address>> e) throws Exception {
                                    AndroidSchedulers.mainThread().scheduleDirect(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (loadingListener != null) loadingListener.startLoad();
                                        }
                                    });
                                    try {
                                        e.onNext(geocoder.getFromLocationName(s, maxResult));
                                    } catch (Exception ex) {
                                        if (loadingListener != null) loadingListener.error(ex);
                                    }
                                    AndroidSchedulers.mainThread().scheduleDirect(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (loadingListener != null) loadingListener.stopLoad();
                                        }
                                    });
                                    e.onComplete();
                                }
                            });
                }
            };
        }
        return findItem;
    }

    private Predicate<String> getRxFilter() {
        if (filter == null) {
            filter = new Predicate<String>() {
                @Override
                public boolean test(String string) throws Exception {
                    if (string.equals(oldValue)) {
                        return false;
                    }
                    oldValue = string;
                    return !(TextUtils.isEmpty(string) || TextUtils.getTrimmedLength(string) == 0);
                }
            };
        }
        return filter;
    }

    //endregion

    private Consumer<List<Address>> getRxConsumer() {
        if (consumer == null) {
            consumer = new Consumer<List<Address>>() {
                @Override
                public void accept(List<Address> addresses) throws Exception {
                    if (addressAdapter == null) return;
                    addressAdapter.clear();
                    addressAdapter.addAll(addresses);
                }
            };
        }
        return consumer;
    }

    //==============================================================================================
    // Static methods
    //==============================================================================================
    //region methods

    /**
     * Get full address from {@link Address}
     */
    public static String getCompleteAddressLine(@Nullable Address address) {
        StringBuilder strReturnedAddress = new StringBuilder("");
        if (address != null) {
            int count = address.getMaxAddressLineIndex();
            if (count == 0) {
                strReturnedAddress.append(address.getAddressLine(0));
                return strReturnedAddress.toString();
            }
            for (int i = 0; i < count; i++) {
                strReturnedAddress.append(address.getAddressLine(i));
                if (i < count - 1) strReturnedAddress.append(", ");
            }
        }
        return strReturnedAddress.toString();
    }

    /**
     * Trim char sequence
     */
    private static CharSequence trim(CharSequence s) {
        if (s == null) return null;

        int len = s.length();

        int start = 0;
        while (start < len && s.charAt(start) <= ' ') {
            start++;
        }

        int end = len;
        while (end > start && s.charAt(end - 1) <= ' ') {
            end--;
        }

        return s.subSequence(start, end);
    }

    //endregion

    public interface OnLocationListener {

        void startLoad();

        void stopLoad();

        void error(Throwable throwable);
    }

    private static class AddressAdapter extends ArrayAdapter<Address> {

        private static final Filter NO_FILTER = new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                return null;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {

            }

            @Override
            public CharSequence convertResultToString(Object resultValue) {
                return super.convertResultToString(getCompleteAddressLine((Address) resultValue));
            }
        };

        AddressAdapter(Context context, int resource, List<Address> objects) {
            super(context, resource, objects);
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            return createView(position, convertView, parent);
        }

        @Override
        public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
            return createView(position, convertView, parent);
        }

        private View createView(int position, View convertView, ViewGroup parent) {
            TextView view;
            if (convertView == null) {
                view = (TextView) LayoutInflater.from(parent.getContext())
                                                .inflate(android.R.layout.simple_dropdown_item_1line, parent, false);
            } else {
                view = (TextView) convertView;
            }

            view.setText(getCompleteAddressLine(getItem(position)));

            return view;
        }

        @NonNull
        @Override
        public Filter getFilter() {
            return NO_FILTER;
        }
    }
}
