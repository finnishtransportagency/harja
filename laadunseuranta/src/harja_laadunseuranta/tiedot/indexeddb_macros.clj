(ns harja-laadunseuranta.tiedot.indexeddb-macros)

(defn- katkaise-keywordista [kw seq]
  (split-with #(not= kw %) seq))

(defmacro with-transaction [db store-names txtype var & body]
  (if (not (contains? #{:readwrite :readonly} txtype))
    (throw (IllegalArgumentException. "transaction type must be :readwrite or :readonly"))
    (let [[body oncomplete] (katkaise-keywordista :on-complete body)]
      `(try
         (let [~var (.transaction ~db (cljs.core/clj->js ~store-names) ~(name txtype))]
           ~(when-not (empty? oncomplete)
              `(set! (.-oncomplete) (fn []
                                      ~(second oncomplete))))
           ~@body)
         (catch :default err# nil)))))

(defmacro with-objectstore [tx store-name var & body]
  `(let [~var (.objectStore ~tx ~store-name)]
     ~@body))

(defmacro with-get-object [store key value-var & body]
  `(let [req# (.get ~store ~key)]
     (set! (.-onsuccess req#) (fn []
                                (let [~value-var (.-result req#)]
                                  ~@body)))))

(defmacro with-cursor [store cursor-var value-var & body]
  (let [[body finally-block] (katkaise-keywordista :finally body)]
    `(aset (.openCursor ~store) "onsuccess"
           (fn [event#]
             (let [~cursor-var (-> event# .-target .-result)]
               (if ~cursor-var 
                 (let [~value-var (cljs.core/js->clj (.-value ~cursor-var))]
                   ~@body)
                 ~(second finally-block)))))))

(defmacro with-count [store count-var & body]
  `(aset (.count ~store) "onsuccess"
         (fn [event#]
           (let [~count-var (-> event# .-target .-result)]
             ~@body))))

(defmacro with-n-items [store n value-var & body]
  `(let [c# (cljs.core/atom [])
         fin# (fn [~value-var]
                ~@body)]
     (with-cursor ~store cursor# v#
       (swap! c# #(conj % v#))
       (if (= ~n (count (deref c#)))
         (fin# (deref c#))
         (harja-laadunseuranta.tiedot.indexeddb/cursor-continue cursor#))
       :finally
       (fin# (deref c#)))))

(defmacro with-all-items [store value-var & body]
  `(let [c# (cljs.core/atom [])]
     (with-cursor ~store cursor# v#
       (swap! c# #(conj % v#))
       (harja-laadunseuranta.tiedot.indexeddb/cursor-continue cursor#)
       :finally
       (let [~value-var (deref c#)]
         ~@body))))

(defmacro with-transaction-to-store [db store-name txtype var & body]
  `(with-transaction ~db [~store-name] ~txtype tx#
     (with-objectstore tx# ~store-name ~var
       ~@body)))
