(ns harja.tyokalut.vkm
  "Viitekehysmuuntimen kyselyt (mm. TR-osoitehaku)"
  (:require [cljs.core.async :refer [<! >! chan put! close! alts! timeout]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log]]
            [harja.pvm :as pvm])
  (:require-macros [cljs.core.async.macros :refer [go alt!]]))

(defn- vkm-base-url []
  (if (k/kehitysymparistossa?)
    "https://testiextranet.vayla.fi/vkm/"
    "/vkm/"))

(defn koordinaatti->trosoite-kahdella [[x1 y1] [x2 y2]]
  (k/post! :hae-tr-pisteilla {:x1 x1 :y1 y1 :x2 x2 :y2 y2} nil true))

(let [juokseva-id (atom 0)]
  (defn- vkm-kutsu-id []
    (swap! juokseva-id inc)))

(defn- map->parametrit [parametrit]
  (loop [uri "?"
         [[k v] & parametrit] (seq parametrit)]
    (if-not k
      uri
      (recur (str uri (name k) "=" v "&")
             parametrit))))

(defn- vkm-kutsu
  "Tekee VKM kutsun JSONP muodossa ja palauttaa kanavan tuloksiin.
  Annetun osoitteen tulee olla suhteellinen VKM:n osoitteeseen. Parametrit
  on avainsanamäppi parametrejä."
  [uri parametrit]
  (let [kutsu-id (vkm-kutsu-id)
        callback (str "vkm_tulos_" kutsu-id)
        kutsu-url (str (vkm-base-url) uri
                       (map->parametrit
                         (assoc parametrit
                           :callback callback)))
        _ (log "TEHDÄÄN VKM KUTSU: " kutsu-url)
        s (doto (.createElement js/document "script")
            (.setAttribute "type" "text/javascript")
            (.setAttribute "src" kutsu-url))
        ch (chan)
        tulos #(do
                 (log "VKM VASTAUS: " (pr-str %))
                 (put! ch (js->clj %))
                 (close! ch)
                 (.removeChild (.-head js/document) s)
                 (aset js/window callback nil))]
    (aset js/window callback tulos)
    (.appendChild (.-head js/document) s)
    (go (first (alts! [ch (timeout 5000)])))))

(defn koordinaatti->tieosoite
  "Muuntaa annetun koordinaatin tieosoitteeksi."
  [[x y]]
  (vkm-kutsu "tieosoite" {:x x :y y}))


(defn tieosoite
  "Kutsuu tieosoite palvelua tierekisteriosoitteella, osoite sisältää avaimet:

  :numero        tien numero
  :alkuosa       tien osa
  :alkuetaisyys  etäisyys tien alusta
  :loppuosa      loppuosa
  :loppuetaisyys loppuetäisyys

  Palautettavassa datassa:
  \"alkupiste\":{\"tieosoitteet\":[{\"osa\":4,\"etaisyys\":5000,\"ajorata\":1,\"tie\":50,\"point\":{\"y\":6683955.515503107,\"spatialReference\":{\"wkid\":3067},\"x\":377686.44404436304}},{\"osa\":4,\"etaisyys\":5000,\"ajorata\":2,\"tie\":50,\"point\":{\"y\":6683973.695825488,\"spatialReference\":{\"wkid\":3067},\"x\":377681.47636308137}}]},\"loppupiste\":{\"pituus\":1005,\"tieosoitteet\":[{\"osa\":5,\"etaisyys\":100,\"ajorata\":1,\"tie\":50,\"point\":{\"y\":6683992.441209993,\"spatialReference\":{\"wkid\":3067},\"x\":378655.91600080184}},{\"osa\":5,\"etaisyys\":100,\"ajorata\":2,\"tie\":50,\"point\":{\"y\":6684006.147510614,\"spatialReference\":{\"wkid\":3067},\"x\":378652.39605513535}}]},\"lines\":{\"lines\":[{\"paths\":[[[377686,6683955],[377739,6683941],[377873,6683899],[377925,6683887],[378036,6683867],[378142,6683860],[378254,6683864],[378375,6683882],[378470,6683905],[378568,6683946],[378602,6683961],[378655,6683992]]],\"spatialReference\":{\"wkid\":3067}},{\"paths\":[[[377681,6683973],[377740,6683957],[377799,6683937],[377873,6683915],[377984,6683892],[378039,6683884],[378100,6683877],[378253,6683880],[378346,6683894],[378445,6683916],[378496,6683931],[378565,6683959],[378612,6683983],[378652,6684006]]],\"spatialReference\":{\"wkid\":3067}}]}}"
  [{:keys [numero alkuosa alkuetaisyys loppuosa loppuetaisyys] :as tierekisteriosoite}]
  (vkm-kutsu "tieosoite" {:tie numero
                          :osa alkuosa
                          :etaisyys alkuetaisyys
                          :losa loppuosa
                          :let loppuetaisyys}))

(declare virhe?)

(defn tieosoite->sijainti
  "Kutsuu VKM:n kautta tierekisteriosoitetta ja yrittää löytää parhaan sijainnin.
   Palauttaa kanavan, josta sijainnin voi lukea. Virhetapauksessa kanavaan kirjoitetaan virheen kuvaus. "
  [tierekisteriosoite]
  (log "Muunetaan tieosoite sijainniksi: " (pr-str tierekisteriosoite))
  (go (let [tulos (<! (tieosoite tierekisteriosoite))]
        (log "TULOS: " (pr-str tulos))
        (if (virhe? tulos)
          ;; Annetaan virhe ulos sellaisenaan
          tulos

          ;; Onnistui, joten yritetään löytää sijaintia
          (let [osoitteet (some-> tulos
                                  (get "alkupiste")
                                  (get "tieosoitteet"))]
            ;; PENDING: onko jotain heuristiikkaa miten etsiä paras?
            ;; Palautetaan nyt vain alkupisteen ensimmäinen x/y piste
            (log "Osoitteet: " (pr-str osoitteet))
            (some-> osoitteet
                    first
                    (get "point")
                    ((fn [{x "x" y "y"}]
                       {:type :point
                        :coordinates [x y]}))))))))

(defn tieosoite->viiva [trosoite]
  (k/post! :hae-tr-viivaksi trosoite nil true))

(defn tieosoite->piste [trosoite]
  (k/post! :hae-tr-viivaksi trosoite nil true))

(defn koordinaatti->trosoite [[x y]]
  (k/post! :hae-tr-pisteella {:x x :y y} nil true))

(defn virhe?
  "Tarkistaa epäonnistuiko VKM kutsu"
  [tulos]
  (harja.asiakas.kommunikaatio/virhe? tulos))

(defn loytyi?
  "Tarkista että tulos ei ole virhe eikä tyhjä tai pelkkä nil reitti"
  [tulos]
  (and (not (virhe? tulos))
       (not (empty? tulos))
       (not= [nil] tulos)))

(def pisteelle-ei-loydy-tieta "Pisteelle ei löydy tietä.")
(def vihje-zoomaa-lahemmas "Yritä zoomata lähemmäs.")

(defn muunna-tierekisteriosoitteet-eri-paivan-verkolle
  "Muuntaa annetut tieosoitteet tilannepäivän mukaiselta verkolta kohdepäivän verkolle.
  Osoitteet annetaan mappina esim. muodossa:
  {:tieosoitteet: [{:tunniste \"**1**\" :tie 50 :osa 5 :etaisyys 0 :ajorata 1}
                  {:tunniste \"**2**\" :tie 50 :osa 5 :etaisyys 100 :ajorata 0}]}
  Palauttaa kanavan, josta vastaus voidaan lukea."
  [tieosoitteet tilannepvm kohdepvm]
  (let [parametrit {:in "tieosoite"
                    :out "tieosoite"
                    :callback "jsonp"
                    :tilannepvm (pvm/pvm tilannepvm)
                    :kohdepvm (pvm/pvm kohdepvm)
                    :alueetpois nil
                    :json (.stringify js/JSON (clj->js tieosoitteet))}]
    (vkm-kutsu "muunnos" parametrit)))

(defn tieosien-pituudet
  ([tie] (tieosien-pituudet tie nil nil))
  ([tie aosa losa]
   (k/post! :hae-tr-osien-pituudet
            {:tie tie
             :aosa aosa
             :losa losa})))

(defn tieosan-ajoradat [tie osa]
  (k/post! :hae-tr-osan-ajoradat {:tie tie :osa osa}))
