(ns harja.tyokalut.functor
  "Simppeli functor implementaatio (geneerinen map)"
  #?@(:clj [(:require [clojure.core.async :refer [go <!]])]
      :cljs [(:require [cljs.core.async :refer [<!]])
             (:require-macros [cljs.core.async.macros :refer [go]])]))

(defprotocol Functor
  (fmap- [this f]
    "Map f over the items in this container and return a container of the same type."))

(extend-protocol Functor
  #?(:clj clojure.lang.ASeq :cljs cljs.core.List)
  (fmap- [this f]
    (reverse (into (list) (map f this))))

  #?(:clj clojure.lang.LazySeq :cljs cljs.core.LazySeq)
  (fmap- [this f]
    (map f this))

  #?(:clj clojure.lang.APersistentVector :cljs cljs.core.PersistentVector)
  (fmap- [this f]
    (mapv f this))

  #?(:clj clojure.lang.APersistentMap :cljs cljs.core.PersistentArrayMap)
  (fmap- [this f]
    (reduce-kv (fn [m k v]
                 (assoc m k (f v))) {} this))

  #?(:clj clojure.lang.APersistentSet :cljs cljs.core.PersistentHashSet)
  (fmap- [this f]
    (into #{}
          (map f this)))

  #?(:clj clojure.lang.Fn :cljs cljs.core.Fn)
  (fmap- [this f]
    (comp f this))

  #?(:clj clojure.lang.Keyword :cljs cljs.core.Keyword)
  (fmap- [this f]
    (comp f this))

  #?@(:clj
      [java.util.concurrent.Future
       (fmap- [this f]
              (future (f @this)))])

  #?(:cljs cljs.core.async.impl.channels.ManyToManyChannel
     :clj clojure.core.async.impl.channels.ManyToManyChannel)
  (fmap- [this f]
    (go (f (<! this)))))

(defn fmap
  "Map f over the items in the given container. Returns a container of the same type as the input."
  [f container]
  (fmap- container f))
