"use client"

import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { FileText, BarChart2, Clock, Shield, LogIn } from "lucide-react"
import Logo from '/public/Logo.png'
import About2 from '/public/image2.png'
import About from '/public/image3.png'
interface HomeProps {
    onLogin: () => void
}

export default function Home({ onLogin }: HomeProps) {
    return (
        <div className="min-h-screen bg-white w-full mx-auto">
            {/* Header */}
            <header className="sticky top-0 z-50   border-b border-gray-200 bg-white">
                <div className="container mx-auto flex h-16 items-center justify-between">
                    <div className="flex items-center gap-2">
                        <img
                            src={Logo }
                            alt="Davis & Shirtliff Logo"
                            width={180}
                            height={50}
                            className="h-auto"
                        />
                    </div>
                    <nav className="hidden md:flex gap-6">
                        <a href="#features" className="text-sm font-medium text-gray-700 hover:text-[#00A0E3] transition-colors">
                            Features
                        </a>
                        <a href="#benefits" className="text-sm font-medium text-gray-700 hover:text-[#00A0E3] transition-colors">
                            Benefits
                        </a>
                        <a href="#about" className="text-sm font-medium text-gray-700 hover:text-[#00A0E3] transition-colors">
                            About
                        </a>
                    </nav>
                    <Button className="bg-[#00A0E3] hover:bg-[#0089c3] text-white" onClick={onLogin}>
                        <LogIn className="mr-2 h-4 w-4" /> Login
                    </Button>
                </div>
            </header>

            <main className="flex-1 ">
                {/* Hero Section */}
                <section className="w-full py-12 md:py-24 lg:py-32 bg-[#e6f4fb]">
                    <div className="container mx-auto px-4 md:px-6">
                        <div className="grid gap-6 lg:grid-cols-2 lg:gap-12 items-center">
                            <div className="space-y-4">
                                <div className="inline-block rounded-lg bg-[#cce9f6] px-3 py-1 text-sm text-[#00A0E3]">
                                    ERP Document Verification
                                </div>
                                <h1 className="text-3xl font-bold tracking-tighter text-gray-900 sm:text-4xl md:text-5xl">
                                    Automated ERP Job Document Verification
                                </h1>
                                <p className="text-gray-600 md:text-xl/relaxed lg:text-base/relaxed xl:text-xl/relaxed">
                                    Streamline your document verification process by comparing Microsoft Dynamics 365 Business Central Job
                                    Ledger Entries with PDF documents.
                                </p>
                                <div className="flex flex-wrap gap-3">
                                    <div className="flex items-center text-sm text-gray-600">
                                        <FileText className="mr-1 h-4 w-4 text-[#00A0E3]" />
                                        Job Consumption
                                    </div>
                                    <div className="flex items-center text-sm text-gray-600">
                                        <FileText className="mr-1 h-4 w-4 text-[#00A0E3]" />
                                        Proforma Invoice
                                    </div>
                                    <div className="flex items-center text-sm text-gray-600">
                                        <FileText className="mr-1 h-4 w-4 text-[#00A0E3]" />
                                        Sales Quote
                                    </div>
                                </div>
                                <div className="flex flex-col sm:flex-row gap-3">
                                    <Button className="bg-[#00A0E3] hover:bg-[#0089c3] text-white" onClick={onLogin}>
                                        Get Started
                                    </Button>
                                    <Button
                                        variant="outline"
                                        className="border-[#00A0E3] text-[#00A0E3] hover:bg-[#e6f4fb]"
                                        onClick={onLogin}
                                    >
                                        Request Demo
                                    </Button>
                                </div>
                            </div>
                            <div className="flex justify-center">
                                <img
                                    src={About2}
                                    alt="ERP Document Verification System"
                                    width={400}
                                    height={400}
                                    className="rounded-lg object-cover"
                                />
                            </div>
                        </div>
                    </div>
                </section>

                {/* Features Section */}
                <section id="features" className="w-full py-12 md:py-24 lg:py-32 bg-white">
                    <div className="container mx-auto px-4 md:px-6">
                        <div className="flex flex-col items-center justify-center space-y-4 text-center">
                            <div className="space-y-2">
                                <h2 className="text-3xl font-bold tracking-tighter text-gray-900 sm:text-5xl">System Features</h2>
                                <p className="max-w-[900px] text-gray-600 md:text-xl/relaxed lg:text-base/relaxed xl:text-xl/relaxed">
                                    Our ERP document verification system offers a comprehensive set of features to streamline your
                                    verification process.
                                </p>
                            </div>
                        </div>
                        <div className="mx-auto grid max-w-5xl gap-8 py-12">
                            <Card className="border-gray-200 bg-white">
                                <CardHeader>
                                    <CardTitle className="text-gray-900">Key Features</CardTitle>
                                    <CardDescription className="text-gray-500">Comprehensive verification capabilities</CardDescription>
                                </CardHeader>
                                <CardContent className="space-y-4">
                                    <div className="grid gap-4 md:grid-cols-2">
                                        <div className="flex items-start gap-2">
                                            <div className="mt-1 rounded-full bg-[#cce9f6] p-1">
                                                <FileText className="h-4 w-4 text-[#00A0E3]" />
                                            </div>
                                            <div>
                                                <h4 className="font-medium text-gray-900">Automated Document Comparison</h4>
                                                <p className="text-sm text-gray-600">
                                                    Automatically compare data between Business Central Job Ledger Entries and PDF documents.
                                                </p>
                                            </div>
                                        </div>
                                        <div className="flex items-start gap-2">
                                            <div className="mt-1 rounded-full bg-[#cce9f6] p-1">
                                                <FileText className="h-4 w-4 text-[#00A0E3]" />
                                            </div>
                                            <div>
                                                <h4 className="font-medium text-gray-900">Multiple Document Support</h4>
                                                <p className="text-sm text-gray-600">
                                                    Verify Job Consumption, Proforma Invoice, and Sales Quote documents against BC data.
                                                </p>
                                            </div>
                                        </div>
                                        <div className="flex items-start gap-2">
                                            <div className="mt-1 rounded-full bg-[#cce9f6] p-1">
                                                <BarChart2 className="h-4 w-4 text-[#00A0E3]" />
                                            </div>
                                            <div>
                                                <h4 className="font-medium text-gray-900">Web-Based Monitoring</h4>
                                                <p className="text-sm text-gray-600">
                                                    Monitor verification status and results through an intuitive web interface.
                                                </p>
                                            </div>
                                        </div>
                                        <div className="flex items-start gap-2">
                                            <div className="mt-1 rounded-full bg-[#cce9f6] p-1">
                                                <Clock className="h-4 w-4 text-[#00A0E3]" />
                                            </div>
                                            <div>
                                                <h4 className="font-medium text-gray-900">Asynchronous Processing</h4>
                                                <p className="text-sm text-gray-600">
                                                    Backend service handles verification logic asynchronously for optimal performance.
                                                </p>
                                            </div>
                                        </div>
                                    </div>
                                </CardContent>
                            </Card>
                        </div>
                    </div>
                </section>

                {/* Benefits Section */}
                <section id="benefits" className="w-full py-12 md:py-24 lg:py-32 bg-[#e6f4fb]">
                    <div className="container mx-auto px-4 md:px-6">
                        <div className="flex flex-col items-center justify-center space-y-4 text-center">
                            <div className="space-y-2">
                                <h2 className="text-3xl font-bold tracking-tighter text-gray-900 sm:text-5xl">System Benefits</h2>
                                <p className="max-w-[900px] text-gray-600 md:text-xl/relaxed lg:text-base/relaxed xl:text-xl/relaxed">
                                    Discover how our ERP document verification system can transform your business processes.
                                </p>
                            </div>
                        </div>
                        <div className="mx-auto grid max-w-5xl gap-8 py-12 md:grid-cols-3">
                            <Card className="border-gray-200 bg-white">
                                <CardHeader>
                                    <Shield className="h-12 w-12 text-[#00A0E3]" />
                                    <CardTitle className="mt-4 text-gray-900">Enhanced Accuracy</CardTitle>
                                </CardHeader>
                                <CardContent>
                                    <p className="text-sm text-gray-600">
                                        Eliminate human error by automating the verification process between Business Central data and PDF
                                        documents.
                                    </p>
                                </CardContent>
                            </Card>
                            <Card className="border-gray-200 bg-white">
                                <CardHeader>
                                    <Clock className="h-12 w-12 text-[#00A0E3]" />
                                    <CardTitle className="mt-4 text-gray-900">Time Savings</CardTitle>
                                </CardHeader>
                                <CardContent>
                                    <p className="text-sm text-gray-600">
                                        Reduce the time spent on manual document verification by up to 80%, allowing your team to focus on
                                        higher-value tasks.
                                    </p>
                                </CardContent>
                            </Card>
                            <Card className="border-gray-200 bg-white">
                                <CardHeader>
                                    <BarChart2 className="h-12 w-12 text-[#00A0E3]" />
                                    <CardTitle className="mt-4 text-gray-900">Improved Compliance</CardTitle>
                                </CardHeader>
                                <CardContent>
                                    <p className="text-sm text-gray-600">
                                        Maintain consistent verification standards and create an audit trail for all document verifications.
                                    </p>
                                </CardContent>
                            </Card>
                        </div>
                    </div>
                </section>

                {/* About Section */}
                <section id="about" className="w-full py-12 md:py-24 lg:py-32 bg-white">
                    <div className="container mx-auto px-4 md:px-6">
                        <div className="flex flex-col items-center justify-center space-y-4 text-center">
                            <div className="space-y-2">
                                <h2 className="text-3xl font-bold tracking-tighter text-gray-900 sm:text-5xl">About The System</h2>
                                <p className="max-w-[900px] text-gray-600 md:text-xl/relaxed lg:text-base/relaxed xl:text-xl/relaxed">
                                    Our ERP document verification system automates the comparison between Microsoft Dynamics 365 Business
                                    Central Job Ledger Entries and associated PDF documents.
                                </p>
                            </div>
                        </div>
                        <div className="mx-auto grid max-w-5xl items-center gap-6 py-12 lg:grid-cols-2 lg:gap-12">
                            <img
                                src={About}
                                alt="Davis & Shirtliff Office"
                                width={550}
                                height={310}
                                className="mx-auto aspect-video overflow-hidden rounded-xl object-cover object-center sm:w-full"
                            />
                            <div className="flex flex-col justify-center space-y-4">
                                <ul className="grid gap-6">
                                    <li>
                                        <div className="grid gap-1">
                                            <h3 className="text-xl font-bold text-gray-900">System Overview</h3>
                                            <p className="text-gray-600">
                                                A comprehensive solution for verifying ERP job documents by comparing data between Business
                                                Central and PDF documents.
                                            </p>
                                        </div>
                                    </li>
                                    <li>
                                        <div className="grid gap-1">
                                            <h3 className="text-xl font-bold text-gray-900">Key Components</h3>
                                            <p className="text-gray-600">
                                                Web frontend for monitoring and triggering verifications, and a backend service for handling
                                                verification logic asynchronously.
                                            </p>
                                        </div>
                                    </li>
                                    <li>
                                        <div className="grid gap-1">
                                            <h3 className="text-xl font-bold text-gray-900">Integration</h3>
                                            <p className="text-gray-600">
                                                Seamlessly integrates with Microsoft Dynamics 365 Business Central and your existing document
                                                management system.
                                            </p>
                                        </div>
                                    </li>
                                </ul>
                            </div>
                        </div>
                    </div>
                </section>

                {/* CTA Section */}
                <section className="w-full py-12 md:py-24 lg:py-32 bg-[#e6f4fb]">
                    <div className="container mx-auto px-4 md:px-6">
                        <div className="flex flex-col items-center justify-center space-y-4 text-center">
                            <div className="space-y-2">
                                <h2 className="text-3xl font-bold tracking-tighter text-gray-900 sm:text-5xl">Ready to Get Started?</h2>
                                <p className="max-w-[600px] text-gray-600 md:text-xl/relaxed lg:text-base/relaxed xl:text-xl/relaxed">
                                    Start streamlining your document verification process today.
                                </p>
                            </div>
                            <div className="flex flex-col sm:flex-row gap-3">
                                <Button size="lg" className="bg-[#00A0E3] hover:bg-[#0089c3] text-white" onClick={onLogin}>
                                    <LogIn className="mr-2 h-4 w-4" /> Login to System
                                </Button>
                                <Button
                                    size="lg"
                                    variant="outline"
                                    className="border-[#00A0E3] text-[#00A0E3] hover:bg-[#e6f4fb]"
                                    onClick={onLogin}
                                >
                                    Request a Demo
                                </Button>
                            </div>
                        </div>
                    </div>
                </section>
            </main>

            {/* Footer */}
            <footer className="w-full border-t border-gray-200 bg-white py-6">
                <div className="container mx-auto flex flex-col items-center justify-between gap-4 md:flex-row px-4 md:px-6">
                    <div className="flex items-center gap-2">
                        <img
                            src={Logo}
                            alt="Davis & Shirtliff Logo"
                            width={120}
                            height={35}
                            className="h-auto"
                        />
                        <p className="text-sm text-gray-500">
                            &copy; {new Date().getFullYear()} Davis & Shirtliff. All rights reserved.
                        </p>
                    </div>
                    <div className="flex gap-4">
                        <a href="#" className="text-sm text-gray-500 hover:text-[#00A0E3]">
                            Privacy Policy
                        </a>
                        <a href="#" className="text-sm text-gray-500 hover:text-[#00A0E3]">
                            Terms of Service
                        </a>
                        <a href="#" className="text-sm text-gray-500 hover:text-[#00A0E3]">
                            Contact Us
                        </a>
                    </div>
                </div>
            </footer>
        </div>
    )
}
