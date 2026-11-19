package com.posapp.onlinestore;

import java.util.List;
import java.util.Map;

/**
 * Starter texts of the shop's information pages, in English and French. They are general and short: staff are told to read them, adapt them to the shop and,
 * where the law asks, have them checked. Text written as paragraphs; a line starting with "## " is a heading and a line starting with "- " is a bullet.
 * {shop}, {email}, {phone} and {address} are replaced by the shop's own details when the page is shown.
 */
final class OnlineInfoDefaults {

    /** The pages in the order they are shown. */
    static final List<String> SLUGS = List.of("terms", "privacy", "returns", "delivery");

    private static final Map<String, String[]> EN = Map.of(
            "terms", new String[]{"Terms & Conditions", """
## About these terms
By placing an order on the {shop} online shop you accept these terms. If you do not agree, please do not order.

## Orders
An order is a request to buy. It becomes a sale when the shop accepts it; you are told by message or e-mail. The shop may refuse or cancel an order, for example when an item is no longer available or the delivery details are not clear. If you already paid, you are refunded.

## Prices and payment
Prices are shown in the shop's currency and include the taxes shown at checkout. The price and stock shown when you order are the ones that count. You can pay with the methods offered at checkout (mobile money, cash on delivery or pick-up, where available).

## Delivery and pick-up
See the Delivery page. You must give an address and a phone number where we can reach you.

## Returns and refunds
See the Returns page.

## Your account and data
See the Privacy page.

## Contact
{shop} · {phone} · {email}
"""},
            "privacy", new String[]{"Privacy Policy", """
## What we collect
When you order we ask for your name, phone number, delivery address and, if you give it, your e-mail address. If you create an account, we also keep your saved addresses, wishlist and order history. If you write to us, we keep your message.

## Why we use it
Only to take and deliver your orders, to tell you how your order is going, to give you the invoice and support, and, if you ask for it, to send you our newsletter. We do not sell your data.

## Who sees it
The staff of {shop} and, when needed to complete an order, the delivery person and the payment service you chose (for example mobile money).

## Cookies and your browser
The shop keeps your cart, wishlist, language and recently viewed products in your own browser so they are there when you come back. You can clear them at any time in your browser settings.

## How long we keep it
Orders and invoices are kept as long as the law asks for accounting. Messages and newsletter addresses are kept until you ask us to remove them or you unsubscribe.

## Your choices
You may ask to see, correct or delete your data, or to stop the newsletter (every newsletter has an unsubscribe link). Send us a message from the Contact page of the shop, or use the phone number or e-mail shown there.
"""},
            "returns", new String[]{"Returns & Refunds", """
## Before you accept the goods
Please check your order when it arrives. If an item is damaged, wrong or missing, tell us the same day.

## Returns
You may ask to return an item within 7 days of receiving it if it is unused, in its original packaging and you have the order code. Some goods cannot be returned for hygiene or safety reasons, or because they were made for you.

## How to ask
Open your order from the "Track your order" box with your order code, or contact us with the code. We tell you if the return is accepted and where to bring the item.

## Refunds
A refund is made to the way you paid (mobile money back to the same number, or cash in the shop for cash orders) after we receive and check the item. The delivery fee is refunded only when the mistake is ours.

## Contact
{shop} · {phone} · {email}
"""},
            "delivery", new String[]{"Delivery & Shipping", """
## Where and how
We deliver to the areas listed at checkout. Each area has its own delivery fee and sometimes a minimum order. You can also pick the goods up in the shop when pick-up is offered. {address}

## How long it takes
The estimated delivery time is shown on the product page and at checkout. It counts from the day the shop accepts your order. It is an estimate, not a promise: weather, roads and stock can change it.

## Fees
The delivery fee is added at checkout. Some orders above an amount are delivered free when the shop offers it.

## When we deliver
Please keep your phone on; the delivery person will call you. If nobody can receive the order, we will try to agree on a new time with you.

## Questions
{shop} · {phone} · {email}
"""});

    private static final Map<String, String[]> FR = Map.of(
            "terms", new String[]{"Conditions générales", """
## À propos de ces conditions
En passant une commande sur la boutique en ligne {shop}, vous acceptez ces conditions. Si vous n'êtes pas d'accord, merci de ne pas commander.

## Commandes
Une commande est une demande d'achat. Elle devient une vente quand la boutique l'accepte ; vous êtes prévenu par message ou par e-mail. La boutique peut refuser ou annuler une commande, par exemple si un article n'est plus disponible ou si les informations de livraison ne sont pas claires. Si vous avez déjà payé, vous êtes remboursé.

## Prix et paiement
Les prix sont affichés dans la devise de la boutique et comprennent les taxes indiquées à la commande. Le prix et le stock affichés au moment de la commande sont ceux qui comptent. Vous pouvez payer avec les moyens proposés à la commande (mobile money, paiement à la livraison ou au retrait, selon le cas).

## Livraison et retrait
Voir la page Livraison. Vous devez donner une adresse et un numéro de téléphone où nous pouvons vous joindre.

## Retours et remboursements
Voir la page Retours.

## Votre compte et vos données
Voir la page Confidentialité.

## Contact
{shop} · {phone} · {email}
"""},
            "privacy", new String[]{"Politique de confidentialité", """
## Ce que nous recueillons
Quand vous commandez, nous demandons votre nom, votre numéro de téléphone, votre adresse de livraison et, si vous le donnez, votre e-mail. Si vous créez un compte, nous gardons aussi vos adresses enregistrées, votre liste de souhaits et l'historique de vos commandes. Si vous nous écrivez, nous gardons votre message.

## Pourquoi nous l'utilisons
Uniquement pour prendre et livrer vos commandes, vous dire où en est votre commande, vous donner la facture et l'assistance et, si vous le demandez, vous envoyer notre lettre d'information. Nous ne vendons pas vos données.

## Qui les voit
Le personnel de {shop} et, quand c'est nécessaire pour terminer une commande, le livreur et le service de paiement que vous avez choisi (par exemple le mobile money).

## Cookies et votre navigateur
La boutique garde votre panier, votre liste de souhaits, votre langue et les produits consultés récemment dans votre propre navigateur pour les retrouver à votre retour. Vous pouvez les effacer à tout moment dans les réglages de votre navigateur.

## Durée de conservation
Les commandes et les factures sont gardées aussi longtemps que la loi l'exige pour la comptabilité. Les messages et les adresses de la lettre d'information sont gardés jusqu'à ce que vous demandiez leur suppression ou que vous vous désabonniez.

## Vos choix
Vous pouvez demander à voir, corriger ou supprimer vos données, ou arrêter la lettre d'information (chaque lettre a un lien de désabonnement). Envoyez-nous un message depuis la page Contact de la boutique, ou utilisez le téléphone ou l'e-mail indiqués.
"""},
            "returns", new String[]{"Retours et remboursements", """
## Avant d'accepter la marchandise
Merci de vérifier votre commande à son arrivée. Si un article est abîmé, erroné ou manquant, dites-le-nous le jour même.

## Retours
Vous pouvez demander à retourner un article dans les 7 jours suivant sa réception s'il est inutilisé, dans son emballage d'origine et si vous avez le code de commande. Certains produits ne peuvent pas être retournés pour des raisons d'hygiène ou de sécurité, ou parce qu'ils ont été faits pour vous.

## Comment demander
Ouvrez votre commande depuis la zone « Suivre votre commande » avec votre code, ou contactez-nous avec ce code. Nous vous disons si le retour est accepté et où apporter l'article.

## Remboursements
Le remboursement se fait selon votre mode de paiement (mobile money vers le même numéro, ou espèces en boutique pour les commandes payées en espèces) après réception et contrôle de l'article. Les frais de livraison ne sont remboursés que si l'erreur vient de nous.

## Contact
{shop} · {phone} · {email}
"""},
            "delivery", new String[]{"Livraison et expédition", """
## Où et comment
Nous livrons dans les zones indiquées à la commande. Chaque zone a ses frais de livraison et parfois un minimum de commande. Vous pouvez aussi retirer la marchandise en boutique quand le retrait est proposé. {address}

## Délai
Le délai estimé est affiché sur la page du produit et à la commande. Il compte à partir du jour où la boutique accepte votre commande. C'est une estimation, pas une promesse : la météo, les routes et le stock peuvent le modifier.

## Frais
Les frais de livraison sont ajoutés à la commande. Certaines commandes au-dessus d'un montant sont livrées gratuitement quand la boutique le propose.

## Jour de la livraison
Gardez votre téléphone allumé ; le livreur vous appellera. Si personne ne peut recevoir la commande, nous essaierons de convenir d'un nouveau moment avec vous.

## Questions
{shop} · {phone} · {email}
"""});

    private OnlineInfoDefaults() {
    }

    static boolean known(String slug) {
        return SLUGS.contains(slug);
    }

    /** {title, body} of the starter text in the language ("fr" or English). */
    static String[] of(String slug, String lang) {
        return ("fr".equalsIgnoreCase(lang) ? FR : EN).get(slug);
    }
}
