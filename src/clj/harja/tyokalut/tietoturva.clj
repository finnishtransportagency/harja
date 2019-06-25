(ns harja.tyokalut.tietoturva
  (:require [specql.core :refer [fetch]]
            [specql.core :as specql]))

;; Usein nämä linkkitarkistukset on tehty käsin eri palveluissa queryttämällä eri tauluja.
;; Jatkossa voisi kuitenkin käyttää aina näitä geneerisiä apureita.

(defn vaadi-linkki-id [vaadittu-linkki-id taulu linkitys-sarake]
  (assert vaadittu-linkki-id (str "Linkki-id puuttuu tarkistettaessa taulun " taulu " linkkiä " linkitys-sarake)))

(defn vaadi-linkitys
  "Tarkistaa, että taulussa oleva rivi linkittyy annettuun id-arvoon.
   Jos ei linkity, heittää poikkeuksen.
   Jos linkittyvää id:tä ei anneta, ei tee mitään.
   Jos linkitys-id puuttuu, heittää poikkeuksen (älä kutsu jos linkitystä ei ole olemassa)

   Tällä voi tarkistaa esim. sen, että päivitettävä rivi kuuluu siihen urakkaan, johon
   oikeustarkistus on tehty."
  [db taulu id-sarake id-arvo linkitys-sarake vaadittu-linkki-id]
  (vaadi-linkki-id vaadittu-linkki-id taulu linkitys-sarake)

  (when id-arvo ;; Jos id:tä ei ole annettu, linkitystä on turha tarkistaa
    (let [taulu-rivi (first (specql/fetch db taulu
                                          #{linkitys-sarake}
                                          {id-sarake id-arvo}))
          linkki-id (linkitys-sarake taulu-rivi)]

      (when (not= linkki-id vaadittu-linkki-id)
        (throw (SecurityException. (str "Annetussa taulussa " taulu
                                        " oleva rivi id:llä " id-arvo
                                        " ei linkity " linkitys-sarake "=" vaadittu-linkki-id
                                        " vaan " linkitys-sarake "=" linkki-id)))))))

(defn vaadi-ainakin-yksi-linkitys
  "Tarkistaa, että taulussa olevista riveistä ainakin yksi linkittyy annettuun id-arvoon.
   Jos ei linkity, heittää poikkeuksen.
   Jos id:tä ei anneta, ei tee mitään.
   Jos linkitys-id puuttuu, heittää poikkeuksen (älä kutsu jos linkitystä ei ole olemassa).

   Tällä voi tarkistaa esim. sen, että linkitystaulussa oleva rivi liittyy annettuun urakkaan."
  [db taulu id-sarake id-arvo linkitys-sarake vaadittu-linkki-id]
  (vaadi-linkki-id vaadittu-linkki-id taulu linkitys-sarake)

  (when id-arvo ;; Jos id:tä ei ole annettu, linkitystä on turha tarkistaa
    (let [taulu-rivit (specql/fetch db taulu
                                    #{linkitys-sarake}
                                    {id-sarake id-arvo})
          linkki-idt (set (map linkitys-sarake taulu-rivit))]

      ;; Linkki-id:t set sisältää vaaditun linkin
      (when-not (linkki-idt vaadittu-linkki-id)
        (throw (SecurityException. (str "Annetussa taulussa " taulu
                                        " oleva rivi id:llä " id-arvo
                                        " ei linkity " linkitys-sarake "=" vaadittu-linkki-id
                                        " vaan " linkitys-sarake "=" linkki-idt)))))))
