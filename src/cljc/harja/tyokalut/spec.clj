(ns harja.tyokalut.spec
  "Makroja specien helpommalle käyttämiselle.
  Määrittelee defn+ ja let+ makrot, joilla määritellään nimen lisäksi arvon spec."
  (:require [clojure.spec.alpha :as s]
            [clojure.core.specs.alpha :as cs]
            ))

(s/def ::specish (s/or :kw keyword?
                       :fn fn?
                       :symbol symbol?))

(s/def ::defn+-args (s/cat :fn-name symbol?
                           :docstring (s/? string?)
                           :args (s/and vector?
                                        #(= 0 (mod (count %) 2))
                                        #(every?
                                          (fn [[name spec]]
                                            (and (s/valid? ::cs/binding-form name)
                                                 (s/valid? ::specish spec)))
                                          (partition 2 %)))
                           :return-spec ::specish
                           :body (s/+ any?)))

(defmacro defn+ [& args]
  (let [{:keys [docstring fn-name args return-spec body] :as conformed}
        (s/conform ::defn+-args args)
        args (partition 2 args)]
    `(do
       (defn ~fn-name ~(or docstring "Hei dokumentoi kamasi") [~@(map first args)]
         ~@body)
       (s/fdef ~fn-name
               :args (s/cat ~@(mapcat
                               (fn [[argname spec]]
                                 [(keyword (str "arg-" (name argname)))
                                  spec])
                               args))
               :ret ~(second return-spec)))))

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
