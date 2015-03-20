(ns karma.adapter
  (:require harja.app-test
            [cljs.test :as test :refer-macros [run-tests] :refer [report]]))

(defn karma []
  (.-__karma__ js/window))

(enable-console-print!)

(def counter-data {:pass 0 :fail 0 :error 0})
(def global-counters (atom counter-data))

(def init-env (assoc counter-data :output nil))
(def test-env (atom init-env))

(defmethod report [:karma :pass] [m]
  (swap! global-counters update-in [:pass] inc)
  (swap! test-env update-in [:pass] inc))

(defn format-output [expected actual message]
  (let [difference (str "expected: " expected ", actual:" actual)]
    (if message
      (str message ". " difference)
      difference)))

(defmethod report [:karma :fail] [{:keys [expected actual message] :as m}]
  (swap! global-counters update-in [:fail] inc)
  (swap! test-env update-in [:fail] inc)
  (swap! test-env assoc :output (format-output expected actual message)))

(defmethod report [:karma :error] [{:keys [expected actual message] :as m}]
  (swap! global-counters update-in [:error] inc)
  (swap! test-env update-in [:error] inc)
  (swap! test-env assoc :output (format-output expected actual message))
  (when expected
    (println "Expected:" expected))
  (println "Got:" actual))

(defmethod report [:karma :begin-test-var] [m]
  (reset! test-env (assoc init-env :start (.getTime (js/Date.))))
  (println "Running test:" (:name (meta (:var m)))))

(defmethod report [:karma :end-test-var] [m]
  (let [end (.getTime (js/Date.))
        start (:start @test-env)
        output (:output @test-env)]
    (println "Finished:" (:name (meta (:var m))))
    (.result (karma) (clj->js {"id" ""
                               "description" (:name (meta (:var m)))
                               "suite" [(:ns (meta (:var m)))]
                               "success" (and (zero? (:fail @test-env))
                                              (zero? (:error @test-env)))
                               "time" (- end start)
                               "log" (if output [output] [])}))))

(defmethod report [:karma :summary] [{:keys [test]}]
  (let [{:keys [pass fail error]} @global-counters]
    (println "Ran" test "tests containing"
             (+ pass fail error) "assertions.")
    (println fail "failures," error "errors.")))

(def test-ns-meta
  [(ns-interns 'harja.app-test)])

(defn count-test-vars [ns-meta-list]
  (reduce + (for [ns-meta ns-meta-list]
              (count (filter (fn [[_ v]] (:test (meta v))) ns-meta)))))

(set! (.-start (.-__karma__ js/window))
      (fn []
        (.info (karma) #js {:total (count-test-vars test-ns-meta)})
        (run-tests (test/empty-env :karma)
                   'harja.app-test)
        (.complete (karma))))
