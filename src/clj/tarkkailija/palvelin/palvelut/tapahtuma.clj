(ns tarkkailija.palvelin.palvelut.tapahtuma
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :as async]
            [clojure.spec.alpha :as s]
            [harja.palvelin.tapahtuma-protokollat :as tapahtumat-p]
            [tarkkailija.palvelin.komponentit.tapahtumat :as tapahtumat]
            [harja.tyokalut.predikaatti :as p]
            [tarkkailija.palvelin.rajapinta-protokolla :as rajapinta]
            [taoensso.timbre :as log])
  (:import [java.lang Math]))

(defn tapahtuman-tarkkailija!
  ([klusterin-tapahtumat tapahtuma]
   (tapahtuman-tarkkailija! klusterin-tapahtumat tapahtuma :perus))
  ([klusterin-tapahtumat tapahtuma tyyppi]
   {:pre [(s/valid? ::tapahtumat/tapahtuma tapahtuma)
          (s/valid? ::tapahtumat/tyyppi tyyppi)]
    :post [(p/chan? %)]}
   (tapahtumat-p/tarkkaile! klusterin-tapahtumat tapahtuma tyyppi)))

(defn yhta-aikaa-tapahtuman-tarkkailija!
  [klusterin-tapahtumat ryhmakutsuntiedot & args]
  (binding [tapahtumat/*tarkkaile-yhta-aikaa* ryhmakutsuntiedot]
    (apply tapahtuman-tarkkailija! klusterin-tapahtumat args)))

(defn tapahtuman-kuuntelija!
  [klusterin-tapahtumat tapahtuma f]
  {:pre [(s/valid? ::tapahtumat/tapahtuma tapahtuma)
         (ifn? f)]
   :post [(or (false? %)
              (fn? %))]}
  (tapahtumat-p/kuuntele! klusterin-tapahtumat tapahtuma f))

(defn julkaise-tapahtuma
  [klusterin-tapahtumat tapahtuma data host-nimi]
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
  (future (let [kuuntelija (async/<!! (tapahtuman-tarkkailija! klusterin-tapahtumat tapahtuma tyyppi))
                timeout-annettu? (not= 0 timeout)]
            (when kuuntelija
              (async/go
                (loop [[arvo portti] (if timeout-annettu?
                                       (async/alts! [kuuntelija
                                                     (async/timeout timeout)])
                                       [(async/<! kuuntelija) kuuntelija])]
                  (when-not (p/chan-closed? kuuntelija)
                    (try (if timeout-annettu?
                           (apply f arvo (not= portti kuuntelija) args)
                           (apply f arvo args))
                         (catch Throwable t
                           (log/error (str "Kuuntelijan go loop kaatui virheeseen kutsuessa funktiota f. " (.getMessage t)))
                           (binding [*out* *err*]
                             (println "Stack trace:"))
                           (.printStackTrace t)
                           (throw t)))
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

(defn lopeta-tapahtuman-kuuntelu [klusterin-tapahtumat kuuntelija]
  (when (p/chan? kuuntelija)
    (tapahtumat-p/lopeta-tarkkailu! klusterin-tapahtumat kuuntelija)
    (async/close! kuuntelija)))

(defn tapahtuma-julkaisija
  "Palauttaa funktion, jolle annettava data julkaistaan aina tässä määritetylle eventille."
  [klusterin-tapahtumat tapahtuma host-nimi]
  (fn [data]
    (julkaise-tapahtuma klusterin-tapahtumat tapahtuma data host-nimi)))

(defn tapahtuma-datan-spec
  "Tarkoitettu wrapperiksi tapahtuma-julkaisija:lle. Julkaistavaksi tarkoitettu data ensin validoidaan tälle annettua spekkiä vasten.
   Jos validointi epäonnistuu, julkaistavaksi dataksi laitetaan {::validointi-epaonnistui [ feilannu data ]}, lisäksi virhe logitetaan."
  [tapahtuma-julkaisija spec]
  (fn [data]
    (if (s/valid? spec data)
      (tapahtuma-julkaisija data)
      (throw (IllegalArgumentException. (str "Data " data " ei oli validi. \n" (s/explain-str spec data)))))))

(defrecord Tapahtuma []
  component/Lifecycle
  (start [{:keys [klusterin-tapahtumat rajapinta] :as this}]
    (rajapinta/lisaa-palvelu rajapinta :tapahtuma-julkaisija (partial tapahtuma-julkaisija klusterin-tapahtumat))
    (rajapinta/lisaa-palvelu rajapinta :tapahtuma-datan-spec tapahtuma-datan-spec)
    (rajapinta/lisaa-palvelu rajapinta :lopeta-tapahtuman-kuuntelu (partial lopeta-tapahtuman-kuuntelu klusterin-tapahtumat))
    (rajapinta/lisaa-palvelu rajapinta :tapahtuman-tarkkailija! (partial tapahtuman-tarkkailija! klusterin-tapahtumat))
    (rajapinta/lisaa-palvelu rajapinta :yhta-aikaa-tapahtuman-tarkkailija! (partial yhta-aikaa-tapahtuman-tarkkailija! klusterin-tapahtumat))
    (rajapinta/lisaa-palvelu rajapinta :tapahtuman-kuuntelija! (partial tapahtuman-kuuntelija! klusterin-tapahtumat))
    (rajapinta/lisaa-palvelu rajapinta :julkaise-tapahtuma (partial julkaise-tapahtuma klusterin-tapahtumat))
    (rajapinta/lisaa-palvelu rajapinta :tarkkaile-tapahtumaa (partial tarkkaile-tapahtumaa klusterin-tapahtumat))
    (rajapinta/lisaa-palvelu rajapinta :tarkkaile-tapahtumia (partial tarkkaile-tapahtumia klusterin-tapahtumat))
    this)
  (stop [{:keys [rajapinta] :as this}]
    (rajapinta/poista-palvelu rajapinta :tapahtuma-julkaisija)
    (rajapinta/poista-palvelu rajapinta :tapahtuma-datan-spec)
    (rajapinta/poista-palvelu rajapinta :lopeta-tapahtuman-kuuntelu)
    (rajapinta/poista-palvelu rajapinta :yhta-aikaa-tapahtuman-tarkkailija!)
    (rajapinta/poista-palvelu rajapinta :tapahtuman-tarkkailija!)
    (rajapinta/poista-palvelu rajapinta :tapahtuman-kuuntelija!)
    (rajapinta/poista-palvelu rajapinta :julkaise-tapahtuma)
    (rajapinta/poista-palvelu rajapinta :tarkkaile-tapahtumaa)
    (rajapinta/poista-palvelu rajapinta :tarkkaile-tapahtumia)
    this))