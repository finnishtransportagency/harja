(ns harja.tyokalut.spec
  "Makroja specien helpommalle käyttämiselle.
  Määrittelee defn+ ja let+ makrot, joilla määritellään nimen lisäksi arvon spec."
  (:require [clojure.spec.alpha :as s]
            [clojure.core.specs.alpha :as cs]
            [clojure.future :refer :all]))

(s/def ::specish (s/or :kw keyword?
                       :fn fn?
                       :symbol symbol?))

(s/def ::defn+-alku (s/or :with-docstring (s/cat :fn-name symbol?
                                                 :docstring string?)
                          :without-docstring (s/cat :fn-name symbol?
                                                    :any any?)))
(s/def ::fn-args (s/and vector?
                        #(= 0 (mod (count %) 2))
                        #(every?
                           (fn [[name spec]]
                             (and (s/valid? ::cs/binding-form name)
                                  (s/valid? ::specish spec)))
                           (partition 2 %))))

(s/def ::defn+-loppu (s/cat :args ::fn-args
                            :return-spec ::specish
                            :body (s/+ any?)))
(s/def ::defn+-args (s/or :one-arity (s/cat :fn-name symbol?
                                            :docstring (s/? string?)
                                            :args ::fn-args
                                            :return-spec ::specish
                                            :body (s/+ any?))
                         :n-arity (s/cat :fn-name symbol?
                                         :docstring (s/? string?)
                                         :bodies (s/+ list?))))

(defmacro defn+ [& args]
  (let [useampi-arity? (if (string? (second args))
                        (vector? (first (nth args 2)))
                        (vector? (first (nth args 1))))
        {:keys [fn-name docstring] :as conformed} (second (s/conform ::defn+-alku (take 2 args)))
        doc-string-jalkeiset-osat (if (nil? docstring) (drop 1 args) (drop 2 args))
        fn+-osat (if useampi-arity?
                  (mapv #(s/conform ::defn+-loppu %) doc-string-jalkeiset-osat)
                  (s/conform ::defn+-loppu doc-string-jalkeiset-osat))
        _ (when (= conformed ::s/invalid)
            (s/explain ::defn+-alku (take 2 args)))
        _ (when (= fn+-osat ::s/invalid)
            (if useampi-arity? (s/explain ::defn+-loppu (first doc-string-jalkeiset-osat)) (s/explain ::defn+-loppu doc-string-jalkeiset-osat)))
        args-paivitys (fn [mappi] (update mappi :args (fn [args] (partition 2 args))))
        argsit-ositettu (if useampi-arity?
                          (mapv #(args-paivitys %) fn+-osat)
                          (args-paivitys fn+-osat))
        fn-osat (if useampi-arity?
                  (map #(apply list (mapv first (:args %)) (:body %))
                       argsit-ositettu)
                  [(apply list (mapv first (:args argsit-ositettu)) (:body argsit-ositettu))])]
    `(do
       (defn ~fn-name ~(or docstring "Hei dokumentoi kamasi")
         ~@fn-osat)
       ~(let [args (if useampi-arity?
                    (reduce #(if (> (count (:args %1)) (count (:args %2)))
                              (:args %1) (:args %2))
                            argsit-ositettu)
                    (:args argsit-ositettu))
              return-spec (second (if useampi-arity?
                                    (:return-spec (first fn+-osat))
                                    (:return-spec fn+-osat)))]
         `(s/fdef ~fn-name
                 :args (s/cat ~@(mapcat
                                 (fn [[argname spec]]
                                   [(keyword (str "arg-" (name argname)))
                                    spec])
                                 args))
                 :ret ~return-spec)))))

(s/fdef defn+
        :args ::defn+-args
        :ret any?)

(comment
  (defn+ int->string
    [i int?] string?
    (str i)))


(s/def ::let+-args (s/cat :bindings (s/and vector?
                                            #(= 0 (mod (count %) 3))
                                            #(every?
                                              (fn [[name spec _]]
                                                (and (s/valid? ::cs/binding-form name)
                                                     (s/valid? ::specish spec)))
                                              (partition 3 %)))
                          :body (s/+ any?)))


(defn- assert-spec [spec value message]
  (assert (s/valid? spec value)
          message)
  value)

(defmacro let+ [& args]
  (let [{:keys [bindings body]} (s/conform ::let+-args args)
        bindings (partition 3 bindings)]
    `(let [~@(mapcat
              (fn [[name spec value]]
                [name `(assert-spec ~spec ~value
                                    ~(str name " ei ole validi " spec))])
              bindings)]
       ~@body)))


(comment (let+ [i int? "1"]
           (+ i 2)))
