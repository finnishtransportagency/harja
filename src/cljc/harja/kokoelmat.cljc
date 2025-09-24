(ns harja.kokoelmat)

(defn swap
  "Swappaa vektorin elementit indekseissä i ja j keskenään."
  [elementit i j]
  (assoc elementit i (get elementit j) j (get elementit i)))

(defn map-by
  "Palauta mappi, jossa `coll`-kokoelman elementit saavat avaimen kutsumalla `key-fn`-funktiota kunkin elementin kohdalla.
  Jos useammalla elementillä on sama avain, vain viimeisin arvo kyseiselle avaimelle sisältyy lopulliseen mappiin."
  [avain-fn coll]
  (into {}
    (map (juxt avain-fn identity))
    coll))

(defn assoc-if
  "Assoc vain mikäli arvo ei ole nil."
  [m k v]
  (if (not (nil? v)) (assoc m k v) m))

(defn assoc-in-if
  "Assoc-in mikäli arvo ei ole nil."
  [m ks v]
  (if (not (nil? v)) (assoc-in m ks v) m))

(defn update-if
  "Kuten update, mutta jos avainta ei ole, palauttaa mapin muokkaamattomana."
  [m k f & args]
  (if (contains? m k)
    (apply (partial update m k f) args)
    m))

(defn contains-in? [m ks]
  (not= ::absent (get-in m ks ::absent)))

(defn update-in-if-contains
  "Kuten update-in, mutta jos annettua polkua ei löydy, palauttaa mapin muokkaamattomana."
  [m ks f & args]
  (if (contains-in? m ks)
    (apply (partial update-in m ks f) args)
    m))

(defn dissoc-in
  "Poistaa arvon sisäkkäisestä rakenteesta, joka on määritelty avainsekvenssillä.
  Mikäli operaatio jättää tyhjäksi jonkin kokoelman, poistetaan kyseinen kokoelma rakenteesta.
  https://github.com/weavejester/medley/blob/master/src/medley/core.cljc"
  ([m ks]
   (if-let [[k & ks] (seq ks)]
     (if (seq ks)
       (let [v (dissoc-in (get m k) ks)]
         (if (empty? v)
           (dissoc m k)
           (assoc m k v)))
       (dissoc m k))
     m))
  ([m ks & kss]
   (if-let [[ks' & kss] (seq kss)]
     (recur (dissoc-in m ks) ks' kss)
     (dissoc-in m ks))))

(defn distinct-by
  "Palauttaa laiskan sekvenssin kokoelman elementeistä poistaen kaikki elementit, jotka
  palauttavat duplikaattiarvoja, kun ne annetaan funktiolle f.
  https://weavejester.github.io/medley/medley.core.html"
  ([f]
   (fn [rf]
     (let [seen (volatile! #{})]
       (fn
         ([] (rf))
         ([result] (rf result))
         ([result x]
          (let [fx (f x)]
            (if (contains? @seen fx)
              result
              (do (vswap! seen conj fx)
                (rf result x)))))))))
  ([f coll]
   (let [step (fn step [xs seen]
                (lazy-seq
                  ((fn [[x :as xs] seen]
                     (when-let [s (seq xs)]
                       (let [fx (f x)]
                         (if (contains? seen fx)
                           (recur (rest s) seen)
                           (cons x (step (rest s) (conj seen fx)))))))
                   xs seen)))]
     (step coll #{}))))
