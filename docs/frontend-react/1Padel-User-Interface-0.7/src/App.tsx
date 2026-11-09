import { Routes, Route, Navigate, useParams } from 'react-router-dom';
import { Layout } from './components/Layout';
import { HomePage, EventsPage } from './pages/HomePage';
import { EventPage, NotFoundPage } from './pages/EventPage';
import { BookingPage } from './pages/BookingPage';
import { LoginPage, RegisterPage, ForgotPasswordPage, LegacyVerifyEmailRedirect, ResetPasswordPage, OAuthErrorPage, CompleteProfilePage } from './pages/AuthPages';
import { MyEventsPage, ProfilePage } from './pages/AccountPages';
import { LivePage } from './pages/LivePage';
import { ReviewPage, PreviewPage } from './pages/ReviewPage';
import { PartnerPage, PartnerInvitationPage } from './pages/PartnerPages';
import { ClubsPage, ClubPage, FuturePage, AppInfoPage, LegalPage, StatusPage, WaitlistOfferPage } from './pages/PublicPages';
function BookingRoute() { const { id } = useParams(); return <BookingPage key={id}/>; }
export function App() {
    return <Routes><Route element={<Layout />}>
    <Route index element={<Navigate to="/home" replace/>}/><Route path="/home" element={<HomePage />}/><Route path="/events" element={<EventsPage />}/><Route path="/schedule" element={<Navigate to="/events" replace/>}/><Route path="/event/:id" element={<EventPage />}/><Route path="/book/:id" element={<BookingRoute />}/>
    <Route path="/login" element={<LoginPage />}/><Route path="/register" element={<RegisterPage />}/><Route path="/forgot-password" element={<ForgotPasswordPage />}/><Route path="/verify-email" element={<LegacyVerifyEmailRedirect />}/><Route path="/reset-password" element={<ResetPasswordPage />}/><Route path="/oauth-error" element={<OAuthErrorPage />}/><Route path="/complete-profile" element={<CompleteProfilePage />}/>
    <Route path="/games" element={<MyEventsPage />}/><Route path="/profile" element={<ProfilePage />}/><Route path="/partners/:id" element={<PartnerPage />}/><Route path="/partner-invitation" element={<PartnerInvitationPage />}/><Route path="/waitlist-offer" element={<WaitlistOfferPage />}/>
    <Route path="/live" element={<LivePage />}/><Route path="/live/:format" element={<LivePage />}/><Route path="/clubs" element={<ClubsPage />}/><Route path="/clubs/:id" element={<ClubPage />}/><Route path="/league" element={<FuturePage kind="league"/>}/><Route path="/ranking" element={<FuturePage kind="ranking"/>}/><Route path="/app-info" element={<AppInfoPage />}/><Route path="/legal/:kind" element={<LegalPage />}/><Route path="/status/:kind" element={<StatusPage />}/>
    <Route path="/review" element={<ReviewPage />}/><Route path="/preview/:id" element={<PreviewPage />}/><Route path="*" element={<NotFoundPage />}/></Route></Routes>;
}
