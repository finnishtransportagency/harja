(ns harja.palvelin.palvelut.tarkastelija.tapahtuma
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :as async]
            [clojure.spec.alpha :as s]
            [harja.fmt :as fmt]
            [harja.palvelin.tapahtuma-protokollat :as tapahtumat-p]
            [harja.palvelin.komponentit.tapahtumat :as tapahtumat]
            [harja.tyokalut.predikaatti :as p]
            [harja.palvelin.komponentit.jarjestelma-rajapinta :as rajapinta]
            [taoensso.timbre :as log])
  (:import [java.net InetAddress]
           [java.lang Math]))

(def ^{:private true
       :dynamic true}
  *log-error* false)

(def host-nimi (fmt/leikkaa-merkkijono 512
                                       (.toString (InetAddress/getLocalHost))))

(defn tapahtuman-julkaisia!
  ([klusterin-tapahtumat tapahtuma]
   (tapahtuman-julkaisia! klusterin-tapahtumat tapahtuma :perus))
  ([klusterin-tapahtumat tapahtuma tyyppi]
   {:pre [(s/valid? ::tapahtumat/tapahtuma tapahtuma)
          (s/valid? ::tapahtumat/tyyppi tyyppi)]
    :post [(p/chan? %)]}
   (tapahtumat-p/tarkkaile! klusterin-tapahtumat tapahtuma tyyppi)))

(defn yhta-aikaa-tapahtuman-julkaisia!
  [klusterin-tapahtumat ryhmanimi & args]
  (binding [tapahtumat/*tarkkaile-yhta-aikaa* ryhmanimi]
    (apply tapahtuman-julkaisia! klusterin-tapahtumat args)))

(defn tapahtuman-kuuntelija!
  [klusterin-tapahtumat tapahtuma f]
  {:pre [(s/valid? ::tapahtumat/tapahtuma tapahtuma)
         (ifn? f)]
   :post [(or (false? %)
              (fn? %))]}
  (tapahtumat-p/kuuntele! klusterin-tapahtumat tapahtuma f))

(defn julkaise-tapahtuma
  [klusterin-tapahtumat tapahtuma data]
  {:pre [(s/valid? ::tapahtumat/tapahtuma tapahtuma)
         (not (nil? data))]
   :post [(boolean? %)]}
  (tapahtumat-p/julkaise! klusterin-tapahtumat tapahtuma data host-nimi))

(defn tarkkaile-tapahtumaa
  "Kuuntelee annettua tapahtumaa ja kutsuu funktiota f tapahtuman arvolla.
   Jos tyyppi on annettu, asetetaan se tarkkailijan tyypiksi.
   Jos timeout on annettu, kutsutaan funktiota f viimeistään timeoutin jälkeen.
   Timeoutin tapahtuessa f saa kahdeksi ensimmäiseksi argumentikseen - nil ja true.
   nil on timeoutista syntyvä arvo ja true vastaa kysymykseen 'menikö timeout loppuun?'.
   Ilman timeouttia f saa ensimmäiseksi argumentikseen tapahtuman julkaissun arvon.
   Lisäksi f:lle annetaan argumenteiksi tässä määritetty args."
  [klusterin-tapahtumat
   tapahtuma
   {:keys [tyyppi timeout]
    :or {tyyppi :perus timeout 0}}
   f
   & args]
  {:post [(future? %)]}
  (future (let [kuuntelija (async/<!! (tapahtuman-julkaisia! klusterin-tapahtumat tapahtuma tyyppi))
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
  [klusterin-tapahtumat & args]
  {:pre [(= 0 (mod (count args) 3))
         (every? (fn [[tapahtuma asetukset f]]
                   (and (s/valid? ::tapahtumat/tapahtuma tapahtuma)
                        (map? asetukset)
                        (ifn? f)))
                 (partition 3 args))]
   :post [(= (count %)
             (Math/round (float (/ (count args) 3))))
          (every? future? %)]}
  (let [tarkkaile-tapahtumaa-args (partition 3 args)]
    (mapv (fn [tarkkaile-tapahtumaa-arg]
            (apply tarkkaile-tapahtumaa klusterin-tapahtumat tarkkaile-tapahtumaa-arg))
          tarkkaile-tapahtumaa-args)))

(defn lopeta-tapahtuman-kuuntelu [kuuntelija]
  (when (p/chan? kuuntelija)
    (async/close! kuuntelija)))

(defn tapahtuma-julkaisija
  "Palauttaa funktion, jolle annettava data julkaistaan aina tässä määritetylle eventille."
  [klusterin-tapahtumat tapahtuma]
  (fn [data]
    (when *log-error*
      (log/error (str "Event: " tapahtuma " sai dataksi: " data)))
    (julkaise-tapahtuma klusterin-tapahtumat tapahtuma data)))

(defn tapahtuma-datan-spec
  "Tarkoitettu wrapperiksi tapahtuma-julkaisija:lle. Julkaistavaksi tarkoitettu data ensin validoidaan tälle annettua spekkiä vasten.
   Jos validointi epäonnistuu, julkaistavaksi dataksi laitetaan {::validointi-epaonnistui [ feilannu data ]}, lisäksi virhe logitetaan."
  [tapahtuma-julkaisija spec]
  (fn [data]
    (if (s/valid? spec data)
      (tapahtuma-julkaisija data)
      (binding [*log-error* true]
        (tapahtuma-julkaisija {::validointi-epaonnistui data})))))

(defrecord Tapahtuma []
  component/Lifecycle
  (start [{:keys [klusterin-tapahtumat rajapinta] :as this}]
    (rajapinta/lisaa rajapinta :tapahtuma-julkaisija (partial tapahtuma-julkaisija klusterin-tapahtumat))
    (rajapinta/lisaa rajapinta :tapahtuma-datan-spec tapahtuma-datan-spec)
    (rajapinta/lisaa rajapinta :lopeta-tapahtuman-kuuntelu lopeta-tapahtuman-kuuntelu)
    (rajapinta/lisaa rajapinta :tapahtuman-julkaisia! (partial tapahtuman-julkaisia! klusterin-tapahtumat))
    (rajapinta/lisaa rajapinta :yhta-aikaa-tapahtuman-julkaisia! (partial yhta-aikaa-tapahtuman-julkaisia! klusterin-tapahtumat))
    (rajapinta/lisaa rajapinta :tapahtuman-kuuntelija! (partial tapahtuman-kuuntelija! klusterin-tapahtumat))
    (rajapinta/lisaa rajapinta :host-nimi (constantly host-nimi))
    (rajapinta/lisaa rajapinta :julkaise-tapahtuma (partial julkaise-tapahtuma klusterin-tapahtumat))
    (rajapinta/lisaa rajapinta :tarkkaile-tapahtumaa (partial tarkkaile-tapahtumaa klusterin-tapahtumat))
    (rajapinta/lisaa rajapinta :tarkkaile-tapahtumia (partial tarkkaile-tapahtumia klusterin-tapahtumat))
    this)
  (stop [{:keys [rajapinta] :as this}]
    (rajapinta/poista rajapinta :tapahtuma-julkaisija)
    (rajapinta/poista rajapinta :tapahtuma-datan-spec)
    (rajapinta/poista rajapinta :lopeta-tapahtuman-kuuntelu)
    (rajapinta/poista rajapinta :yhta-aikaa-tapahtuman-julkaisia!)
    (rajapinta/poista rajapinta :tapahtuman-julkaisia!)
    (rajapinta/poista rajapinta :tapahtuman-kuuntelija!)
    (rajapinta/poista rajapinta :host-nimi)
    (rajapinta/poista rajapinta :julkaise-tapahtuma)
    (rajapinta/poista rajapinta :tarkkaile-tapahtumaa)
    (rajapinta/poista rajapinta :tarkkaile-tapahtumia)
    this))