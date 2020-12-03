(ns harja.palvelin.tyokalut.tapahtuma-apurit
  (:require [clojure.core.async :as async]
            [harja.palvelin.jarjestelma-rajapinta :as jr]
            [harja.fmt :as fmt]
            [certifiable.log :as log])
  (:import [java.net InetAddress]))

(def ^{:private true
       :dynamic true}
  *log-error* false)

(def host-nimi (.getHostName (InetAddress/getLocalHost)))

(defn tapahtuman-tarkkailija!
  ([tapahtuma]
   (jr/kutsu :tapahtuman-tarkkailija! tapahtuma))
  ([tapahtuma tyyppi]
   (jr/kutsu :tapahtuman-tarkkailija! tapahtuma tyyppi)))

(defn tapahtuman-kuuntelija!
  [tapahtuma f]
  (jr/kutsu :tapahtuman-kuuntelija! tapahtuma f))

(defn julkaise-tapahtuma
  [tapahtuma data]
  (log/debug (str "[KOMPONENTTI-EVENT] julkaise-tapahtuma - tapahtuma: " tapahtuma " data: " data))
  (jr/kutsu :julkaise-tapahtuma tapahtuma data host-nimi))

(defn tarkkaile [lopeta-tarkkailu-kanava timeout-ms f]
  (async/go
    (f)
    (loop [[_ kanava] (async/alts! [lopeta-tarkkailu-kanava
                                    (async/timeout timeout-ms)])]
      (when-not (= kanava lopeta-tarkkailu-kanava)
        (f)
        (recur (async/alts! [lopeta-tarkkailu-kanava
                             (async/timeout timeout-ms)]))))))

(defn tarkkaile-tapahtumaa [& args]
  (apply jr/kutsu :tarkkaile-tapahtumaa args))

(defn tarkkaile-tapahtumia
  [& args]
  (apply jr/kutsu :tarkkaile-tapahtumia args))

(defmacro kuuntele-tapahtumia
  "Ottaa argumentikseen n määrän [tapahtuma kasittelija-fn] pareja. Kuuntelee kaikkia annettuja tapahtumia
   ja kutsuu sen tapahtuman kasittelija-fn, joka ensimmäisenä tapahtuu. kasittelija-fn saa argumentikseen
   tapahtuman palauttaman arvon sekä async/chan kanavan, josta se arvo tuli.

   Tämä macro palauttaa kanavan, jonka lukeminen blokkaa siksi aikaa että kaikkia annettuja tapahtumia kuunnellaan.
   Palautettu kanava palauttaa toisen kanavan, jonka sisällön lukeminen blokkaa siksi aikaa, että jokin annetuista
   tapahtumista on tapahtunut.

   tapahtuma      pitää olla :tarkkailija.palvelin.komponentit.tapahtumat/tapahtuma specin mukainen
   kasittelija-fn voi olla joko funktio tai sitten map, jossa avaimet :f ja :tyyppi.
                  :f on funktio
                  :tyyppi on joku :tarkkailija.palvelin.komponentit.tapahtumat/tyyppi specin mukainen keyword.
                          Jos :tyyppi avainta ei ole annettu, annetaan sille arvoksi :perus

   Esimerkki: (kuuntele-useampaa-tapahtumaa :foo (fn [val ch])
                                            :bar {:f (fn [val ch])
                                                  :tyyppi :perus})"
  [& tapahtumakasittelijat]
  (assert (even? (count tapahtumakasittelijat)) "kuuntele-useampaa-tapahtumaa makrolle pitää antaa parillinen määrä argumenttejä")
  (let [tapahtumat# (map (fn [[tapahtuma opts]]
                           (let [f (if (map? opts)
                                     (:f opts)
                                     opts)
                                 tyyppi (if (map? opts)
                                          (get opts :tyyppi :perus)
                                          :perus)]
                             [tapahtuma tyyppi f (gensym "kanava")]))
                         (partition 2 tapahtumakasittelijat))]
    `(async/thread (let [ryhmanimi# (str (gensym "useampi"))
                         kuuntelijat# (mapv (fn [[~'tapahtuma ~'tyyppi]]
                                              (jr/kutsu :yhta-aikaa-tapahtuman-tarkkailija! {:tunnistin ryhmanimi#
                                                                                             :lkm ~(count tapahtumat#)}
                                                        ~'tapahtuma
                                                        ~'tyyppi))
                                            ~(mapv #(vec (take 2 %)) tapahtumat#))
                         ~(mapv #(get % 3) tapahtumat#) (mapv (fn [~'kuuntelija]
                                                                (async/<!! ~'kuuntelija))
                                                              kuuntelijat#)]
                     (async/go (async/alt! ~@(mapcat (fn [[_ _ f s]]
                                                       [s (list ['val 'ch] (list f 'val 'ch))])
                                                     tapahtumat#)))))))

(defn lopeta-tapahtuman-kuuntelu [kuuntelija]
  (jr/kutsu :lopeta-tapahtuman-kuuntelu kuuntelija))

(defn tapahtuma-julkaisija
  [tapahtuma]
  (jr/kutsu :tapahtuma-julkaisija tapahtuma host-nimi))

(defn tapahtuma-datan-spec
  [tapahtuma-julkaisija spec]
  (jr/kutsu :tapahtuma-datan-spec tapahtuma-julkaisija spec))
