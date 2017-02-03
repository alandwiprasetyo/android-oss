package com.kickstarter.viewmodels;

import android.support.annotation.NonNull;
import android.util.Pair;

import com.kickstarter.libs.ActivityViewModel;
import com.kickstarter.libs.Environment;
import com.kickstarter.libs.rx.transformers.Transformers;
import com.kickstarter.libs.utils.ObjectUtils;
import com.kickstarter.models.Project;
import com.kickstarter.models.Update;
import com.kickstarter.services.ApiClientType;
import com.kickstarter.ui.IntentKey;
import com.kickstarter.ui.activities.ProjectUpdatesActivity;

import okhttp3.Request;
import rx.Observable;
import rx.subjects.BehaviorSubject;
import rx.subjects.PublishSubject;

public interface ProjectUpdates {

  interface Inputs {
    /** Call when the web view page url has been intercepted. */
    void pageInterceptedUrl(String url);

    /** Call when a project update comments uri request has been made. */
    void goToCommentsRequest(Request request);

    /** Call when a project update uri request has been made. */
    void goToUpdateRequest(Request request);
  }

  interface Outputs {
    /** Emits an update to start the comments activity with. */
    Observable<Update> startCommentsActivity();

    /** Emits a project and an update to start the update activity with. */
    Observable<Pair<Project, Update>> startUpdateActivity();

    /** Emits a web view url to display. */
    Observable<String> webViewUrl();
  }

  final class ViewModel extends ActivityViewModel<ProjectUpdatesActivity> implements Inputs, Outputs {
    private final ApiClientType client;

    public ViewModel(final @NonNull Environment environment) {
      super(environment);

      client = environment.apiClient();

      final Observable<Project> initialProject = intent()
        .map(i -> i.getParcelableExtra(IntentKey.PROJECT))
        .ofType(Project.class)
        .filter(ObjectUtils::isNotNull);

      initialProject.map(Project::updatesUrl)
        .compose(bindToLifecycle())
        .subscribe(this.webViewUrl::onNext);

      goToCommentsRequestSubject
        .map(this::projectUpdateParams)
        .switchMap(this::fetchUpdate)
        .subscribe(this.startCommentsActivity::onNext);

      final Observable<Update> goToUpdateRequest = goToUpdateRequestSubject
        .map(this::projectUpdateParams)
        .switchMap(this::fetchUpdate);

      initialProject
        .compose(Transformers.takePairWhen(goToUpdateRequest))
        .compose(bindToLifecycle())
        .subscribe(this.startUpdateActivity::onNext);

      initialProject
        .take(1)
        .compose(bindToLifecycle())
        .subscribe(__ -> koala.trackViewedUpdates());
    }

    private @NonNull Observable<Update> fetchUpdate(final @NonNull Pair<String, String> projectAndUpdateParams) {
      return client
        .fetchUpdate(projectAndUpdateParams.first, projectAndUpdateParams.second)
        .compose(Transformers.neverError());
    }

    /**
     * Parses a request for project and update params.
     *
     * @param request   Comments or update request.
     * @return          Pair of project param string and update param string.
     */
    private @NonNull Pair<String, String> projectUpdateParams(final @NonNull Request request) {
      // todo: build a safer param matcher helper--give group names to segments
      final String projectParam = request.url().encodedPathSegments().get(2);
      final String updateParam = request.url().encodedPathSegments().get(4);
      return Pair.create(projectParam, updateParam);
    }

    private final PublishSubject<String> pageInterceptedUrlSubject = PublishSubject.create();
    private final PublishSubject<Request> goToCommentsRequestSubject = PublishSubject.create();
    private final PublishSubject<Request> goToUpdateRequestSubject = PublishSubject.create();

    private final PublishSubject<Update> startCommentsActivity = PublishSubject.create();
    private final PublishSubject<Pair<Project, Update>> startUpdateActivity = PublishSubject.create();
    private final BehaviorSubject<String> webViewUrl = BehaviorSubject.create();

    public final Inputs inputs = this;
    public final Outputs outputs = this;

    @Override public void pageInterceptedUrl(final @NonNull String url) {
      this.pageInterceptedUrlSubject.onNext(url);
    }
    @Override public void goToCommentsRequest(final @NonNull Request request) {
      this.goToCommentsRequestSubject.onNext(request);
    }
    @Override public void goToUpdateRequest(final @NonNull Request request) {
      this.goToUpdateRequestSubject.onNext(request);
    }
    @Override public @NonNull Observable<Update> startCommentsActivity() {
      return startCommentsActivity;
    }
    @Override public @NonNull Observable<Pair<Project, Update>> startUpdateActivity() {
      return startUpdateActivity;
    }
    @Override public @NonNull Observable<String> webViewUrl() {
      return webViewUrl;
    }
  }
}
