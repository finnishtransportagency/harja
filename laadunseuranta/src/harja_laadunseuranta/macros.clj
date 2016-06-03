(ns harja-laadunseuranta.macros)

(defmacro with-channel-read-loop [channel sym & body]
  (let [b (if (= :on-close (first body))
            (drop 2 body)
            body)
        handle-close (if (= :on-close (first body))
                       [(second body)]
                       [])]
   `(let [c# ~channel]
      (cljs.core.async.macros/go-loop [~sym (cljs.core.async/<! c#)]
        (when ~sym
          ~@b
          (recur (cljs.core.async/<! c#)))
        ~@handle-close))))

(defmacro after-delay [delay-ms & body]
  `(cljs.core.async.macros/go (cljs.core.async/<! (cljs.core.async/timeout ~delay-ms))
                              ~@body))

(defmacro with-delay-loop [delay-ms & body]
  `(cljs.core.async.macros/go
     (loop []
       ~@body
       (cljs.core.async/<! (cljs.core.async/timeout ~delay-ms))
       (recur))))
