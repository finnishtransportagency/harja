(ns harja.palvelin.tyokalut.tapahtuma-apurit
  (:require [clojure.core.async :as async]
            [clojure.spec.alpha :as s]
            [harja.fmt :as fmt]
            [harja.palvelin.komponentit.tapahtumat :as tapahtumat]
            [harja.tyokalut.predikaatti :as p]
            [taoensso.timbre :as log])
  (:import [java.net InetAddress]))

(def ^{:private true
       :dynamic true}
  *log-error* false)

(def host-nimi (fmt/leikkaa-merkkijono 512
                                       (.toString (InetAddress/getLocalHost))))

(defn tapahtuman-julkaisia!
  ([tapahtuma]
   (tapahtuman-julkaisia! tapahtuma :perus))
  ([tapahtuma tyyppi]
   {:pre [(s/valid? ::tapahtumat/tapahtuma tapahtuma)
          (s/valid? ::tapahtumat/tyyppi tyyppi)]
    :post [(p/chan? %)]}
   (tapahtumat/tarkkaile! (tapahtumat/tarkkailija) tapahtuma tyyppi)))

(defn tapahtuman-kuuntelija!
  [tapahtuma f]
  {:pre [(s/valid? ::tapahtumat/tapahtuma tapahtuma)
         (ifn? f)]
   :post [(fn? f)]}
  (tapahtumat/kuuntele! (tapahtumat/tarkkailija) tapahtuma f))

(defn julkaise-tapahtuma
  [tapahtuma data]
  {:pre [(s/valid? ::tapahtumat/tapahtuma tapahtuma)
         (not (nil? data))]
   :post [(boolean? %)]}
  (tapahtumat/julkaise! (tapahtumat/tarkkailija) tapahtuma data host-nimi))

(defn tarkkaile [lopeta-tarkkailu-kanava timeout-ms f]
  (async/go
    (loop [[lopetetaan? _] (async/alts! [lopeta-tarkkailu-kanava]
                                        :default false)]
      (when-not lopetetaan?
        (f)
        (async/<! (async/timeout timeout-ms))
        (recur (async/alts! [lopeta-tarkkailu-kanava]
                            :default false))))))

(defn tarkkaile-tapahtumaa
  "Kuuntelee annettua tapahtumaa ja kutsuu funktiota f tapahtuman arvolla.
   Jos tyyppi on annettu, asetetaan se tarkkailijan tyypiksi.
   Jos timeout on annettu, kutsutaan funktiota f viimeistään timeoutin jälkeen.
   Timeoutin tapahtuessa f saa kahdeksi ensimmäiseksi argumentikseen - nil ja true.
   nil on timeoutista syntyvä arvo ja true vastaa kysymykseen 'menikö timeout loppuun?'.
   Ilman timeouttia f saa ensimmäiseksi argumentikseen tapahtuman julkaissun arvon.
   Lisäksi f:lle annetaan argumenteiksi tässä määritetty args."
  [tapahtuma
   {:keys [tyyppi timeout]
    :or {tyyppi :perus timeout 0}}
   f
   & args]
  {:post [(future? %)]}
  (future (let [kuuntelija (async/<!! (tapahtuman-julkaisia! tapahtuma tyyppi))
                timeout-annettu? (not= 0 timeout)]
            (when kuuntelija
              (async/go
                (loop [[arvo portti] (if timeout-annettu?
                                       (async/alts! [kuuntelija
                                                     (async/timeout timeout)])
                                       [(async/<! kuuntelija) kuuntelija])]
                  (when-not (p/chan-closed? kuuntelija)
                    (if timeout-annettu?
                      (apply f arvo (not= portti kuuntelija) args)
                      (apply f arvo args))
                    (recur (if timeout-annettu?
                             (async/alts! [kuuntelija
                                           (async/timeout timeout)])
                             [(async/<! kuuntelija) kuuntelija]))))))
            kuuntelija)))

(defn tarkkaile-tapahtumia
  "Sama kuin tarkkaile-tapahtumaa, mutta useammalle tapahtumalle. Ottaa argumentikseen max 6
   seuraavaa kolmen settiä [tapahtuma kuuntelija-asetukset tapahtuma-funktio].
   Palauttaa kaikille tapahtumille futuret, josta kuuntelija kanavan voi lukea, kun se on valmis.
   Palautetut futuret ovat siinä järjestyksessä, missä tapahtumat annettiin.

   Esimerkki: (tarkkaile-tapahtumia :foo {:tyyppi :perus :timeout 10000} (fn [arvo timeout?])
                                    :bar {} (fn [arvo]))"
  [& args]
  {:pre [(= 0 (mod (count args) 3))
         (every? (fn [[tapahtuma asetukset f]]
                   (and (s/valid? ::tapahtumat/tapahtuma tapahtuma)
                        (map? asetukset)
                        (ifn? f)))
                 (partition 3 args))]
   :post [(= (count %)
             (Math/round (/ (count args) 3)))
          (every? future? %)]}
  (let [tarkkaile-tapahtumaa-args (partition 3 args)]
    (mapv (fn [tarkkaile-tapahtumaa-arg]
            (apply tarkkaile-tapahtumaa tarkkaile-tapahtumaa-arg))
          tarkkaile-tapahtumaa-args)))

(defmacro kuuntele-tapahtumia
  "Ottaa argumentikseen n määrän [tapahtuma kasittelija-fn] pareja. Kuuntelee kaikkia annettuja tapahtumia
   ja kutsuu sen tapahtuman kasittelija-fn, joka ensimmäisenä tapahtuu. kasittelija-fn saa argumentikseen
   tapahtuman palauttaman arvon sekä async/chan kanavan, josta se arvo tuli.

   Tämä macro palauttaa kanavan, jonka lukeminen blokkaa siksi aikaa että kaikkia annettuja tapahtumia kuunnellaan.
   Kanava palauttaa toisen kanavan, jonka sisällön lukeminen blokkaa siksi aikaa, että jokin annetuista
   tapahtumista on käynyt.

   tapahtuma      pitää olla :harja.palvelin.komponentit.tapahtumat/tapahtuma specin mukainen
   kasittelija-fn voi olla joko funktio tai sitten map, jossa avaimet :f ja :tyyppi.
                  :f on funktio
                  :tyyppi on joku :harja.palvelin.komponentit.tapahtumat/tyyppi specin mukainen keyword.
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
    `(async/thread (let [kuuntelijat# (binding [tapahtumat/*tarkkaile-yhta-aikaa* (str (gensym "useampi"))]
                                        (mapv (fn [[~'tapahtuma ~'tyyppi]]
                                                (tapahtuman-julkaisia! ~'tapahtuma ~'tyyppi))
                                              ~(mapv #(vec (take 2 %)) tapahtumat#)))
                         ~(mapv #(get % 3) tapahtumat#) (mapv (fn [~'kuuntelija]
                                                                (async/<!! ~'kuuntelija))
                                                              kuuntelijat#)]
                     (async/go (async/alt!! ~@(mapcat (fn [[_ _ f s]]
                                                        [s (list ['val 'ch] (list f 'val 'ch))])
                                                      tapahtumat#)))))))

(defn lopeta-tapahtuman-kuuntelu [kuuntelija]
  (async/close! kuuntelija))

(defn tapahtuma-julkaisija
  "Palauttaa funktion, jolle annettava data julkaistaan aina tässä määritetylle eventille."
  [tapahtuma]
  (fn [data]
    (when *log-error*
      (log/error (str "Event: " tapahtuma " sai dataksi: " data)))
    (julkaise-tapahtuma tapahtuma data)))

(defn tapahtuma-datan-spec
  "Tarkoitettu wrapperiksi tapahtuma-julkaisija:lle. Julkaistavaksi tarkoitettu data ensin validoidaan tälle annettua spekkiä vasten.
   Jos validointi epäonnistuu, julkaistavaksi dataksi laitetaan {::validointi-epaonnistui [ feilannu data ]}, lisäksi virhe logitetaan."
  [tapahtuma-julkaisija spec]
  (fn [data]
    (if (s/valid? spec data)
      (tapahtuma-julkaisija data)
      (binding [*log-error* true]
        (tapahtuma-julkaisija {::validointi-epaonnistui data})))))
